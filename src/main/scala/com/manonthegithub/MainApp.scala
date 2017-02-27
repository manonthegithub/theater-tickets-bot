package com.manonthegithub

import java.nio.file.Paths
import java.time.temporal.{ChronoUnit, TemporalAdjusters}
import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

import akka.actor.Actor.Receive
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Flow, Sink, Source}
import akka.util.ByteString
import com.manonthegithub.BolshoiPages.{GetAvailableTickets, PerformanceInfo}
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.collection.JavaConversions._
import scala.collection.immutable.TreeMap
import scala.util.{Success, Try}


/**
  * Created by Kirill on 19/10/16.
  */
object MainApp extends App with Actor {

  import ServerApi._

  implicit val system = ActorSystem("theaters")

  implicit val materializer = ActorMaterializer()


  val ServerConnSource = Http().bindAndHandle(route(ref: ActorRef), "0.0.0.0", 8088)


  //  def stream(isTest: Boolean): Source[ByteString, _] = {
  //    if (isTest) {
  //      val file = Paths.get("src/main/resources/test")
  //      println(file.toAbsolutePath.toString)
  //      FileIO.fromPath(file).reduce(_ ++ _)
  //    } else {
  //      BolshoiPages.performanceInfoFromWeb
  //    }
  //  }


  def tickets(isTest: Boolean) = {
    val file = Paths.get("src/main/resources/testTick")
    if (isTest) {
      FileIO.fromPath(file).reduce(_ ++ _)
    } else {
      Source.single(GetAvailableTickets(Uri("http://tickets.bolshoi.ru/ru/performance/11210/")))
        .via(BolshoiPages.performanceTicketsFlow)
    }
  }


  tickets(true).to(Sink.foreach(tickets => {
    tickets.foreach(println)
  })).run()


  //  stream(true).runForeach(b => {
  //    Try {
  //      val jdoc = Jsoup.parse(b.utf8String)
  //      val timetable = timetableFromFullPage(jdoc)
  //      parseElems(timetable)
  //    } match {
  //      case Success(res) =>
  //        res.foreach(e => println(s"$e ${e.status}"))
  //        println("Found to buy:")
  //        findMainPerformanceTickets(res)
  //          .foreach(println)
  //
  //      case Failure(e) => e.printStackTrace()
  //    }
  //  })
  override def receive: Receive = ???
}


object BolshoiPages {

  import akka.http.scaladsl.model.HttpMethods._


  def performanceInfoFlow(implicit sys: ActorSystem, mat: ActorMaterializer): Flow[PerformanceInfoRequest, Seq[PerformanceInfo], NotUsed] = {
    val uri = Uri("http://www.bolshoi.ru/timetable/")
    Flow[PerformanceInfoRequest]
      .map(_ => (HttpRequest(GET, uri), UUID.randomUUID()))
      .via(httpRequestFlow)
      .map(bs => {
        val jdoc = Jsoup.parse(bs.utf8String)
        val timetable = timetableFromFullPage(jdoc)
        parseElems(timetable)
      })
  }

  def performanceTicketsFlow(implicit sys: ActorSystem, mat: ActorMaterializer): Flow[GetAvailableTickets, Seq[Ticket], NotUsed] = {
    Flow[GetAvailableTickets]
      .map(r => (HttpRequest(GET, r.performanceUri), UUID.randomUUID()))
      .via(httpRequestFlow)
      .map(availableTickets)
  }


  private def httpRequestFlow(implicit sys: ActorSystem, mat: ActorMaterializer) = Http()
    .superPool[UUID]()
    .flatMapConcat {
      case ((Success(HttpResponse(StatusCodes.OK, _, entity, _)), _)) =>
        entity.dataBytes.reduce(_ ++ _)
      case ((Success(resp), _)) =>
        resp.entity.discardBytes()
        throw new RuntimeException("error performing request")
    }


  private def availableTickets(rawPage: ByteString): Seq[Ticket] = {
    val jdoc = Jsoup.parse(rawPage.utf8String)
    val elems = jdoc.getElementsByTag("ticket")
    elems.foldLeft(Seq.empty[Ticket])((list, elem) => {

      def getTag(elem: Element, tag: String) = elem
        .getElementsByTag(tag)
        .first()

      def getTheOnlyTag(elem: Element, tag: String) = getTag(elem, tag).text().trim

      def getOptionalTag(elem: Element, tag: String) = Option(getTag(elem, tag)).map(_.text().trim)

      Try {
        val tid = getTheOnlyTag(elem, "tid")
        val region = getTheOnlyTag(elem, "tregion")

        val side = getTheOnlyTag(elem, "tside")
        val place = getTheOnlyTag(elem, "tplace")
          .replace("Место ", "")
          .toInt
        val row = getOptionalTag(elem, "trow")
          .map(_.replace("Ряд ", "").toInt)
        val section = getOptionalTag(elem, "tsection")
        val price = getTheOnlyTag(elem, "tprice1")
          .toInt
        list :+ Ticket(tid, region, side, section, row, place, price)
      }.recover {
        case e => e.printStackTrace()
          list
      }.get
    })

  }

  private def timetableFromFullPage(e: Document) = e.body()
    .getElementsByClass("timetable_content").first()
    .getElementsByTag("tbody").first()

  private def parseElems(timetable: Element) = timetable
    .getElementsByAttributeValueMatching("rel", """^year(\d{4})-month(\d{1,2}).*""")
    .foldLeft(Seq.empty[PerformanceInfo])((list, element) => {
      val date = elemDate(element)
      date match {
        case Some(data) => updateIfRightElement(list, data, element)
        case None => if (list.isEmpty) {
          list
        } else {
          updateIfRightElement(list, list.last.date, element)
        }
      }
    })

  private def updateIfRightElement(seq: Seq[PerformanceInfo],
                                   date: LocalDate,
                                   element: Element) = performanceInfo(element, date) match {
    case Some(info) =>
      seq :+ info
    case None =>
      seq
  }


  private def elemDate(elementByDay: Element) = {
    val pattern = """^year(\d{4})-month(\d{1,2}).*""".r
    Try[LocalDate] {
      elementByDay.attr("rel") match {
        case pattern(year, month) =>
          LocalDate.parse(s"""$year-$month-${day(elementByDay)}""")
      }
    }.toOption
  }

  private def day(elementWithDay: Element) = {
    val num = elementWithDay
      .getElementsByClass("timetable_content__date_date").first()
      .html()
      .takeWhile(_.isDigit)
    if (num.length == 2) {
      num
    } else if (num.length == 1) {
      '0' + num
    } else {
      throw new IllegalArgumentException("invalid element to get day")
    }
  }

  private def performanceInfo(elementByDay: Element, date: LocalDate) = Try {
    elementByDay
      .getElementsByClass("timetable_content__performance_title")
      .text()
  }.toOption.filter(_.nonEmpty).map(name => {
    val ticketsElement = elementByDay.getElementsByClass("timetable_content__tickets").first()
    val placeTimeData = elementByDay
      .getElementsByClass("timetable_content__place")
      .first()
      .textNodes()
      .map(_.text().trim)
    val place = placeTimeData.get(0)
    val time = placeTimeData.get(1)
    PerformanceInfo(
      name,
      date,
      Option(elementByDay.getElementsByClass("timetable_content__performance_description").text).filter(_.nonEmpty),
      place,
      time,
      ticketsElement.text(),
      Option(ticketsElement.getElementsByTag("a").attr("href")).filter(_.nonEmpty)
    )
  })

  sealed abstract class PerformanceInfoRequest

  case object PerformanceInfoRequest extends PerformanceInfoRequest

  case class GetAvailableTickets(performanceUri: Uri)

  case class Ticket(tid: String, region: String, side: String, section: Option[String], row: Option[Int], place: Int, price: Int)

  case class PerformanceInfo(title: String,
                             date: LocalDate,
                             descr: Option[String],
                             place: String,
                             time: String,
                             ticketsStatus: String,
                             linkToBuy: Option[String]) {

    def status: TicketStatus =
      if (isSameTicketStatusString("Купить электронный билет") && linkToBuy.nonEmpty) TicketStatus.CAN_BUY
      else if (isSameTicketStatusString("Скоро в продаже")) TicketStatus.SOON_AVALIABLE
      else if (isSameTicketStatusString("Билетов нет")) TicketStatus.NO_TICKETS
      else TicketStatus.UNIDENTIFIED

    def scene: Scene =
      if (isSameScene("Новая сцена")) Scene.NEW_SCENE
      else if (isSameScene("Бетховенский зал")) Scene.BETHOVEN_SCENE
      else if (isSameScene("Историческая сцена")) Scene.HISTORICAL_SCENE
      else Scene.UNDEFINED

    def equalNameWith(other: PerformanceInfo) = convertedString(this.title) == convertedString(other.title)

    private def isSameTicketStatusString(expectedStatus: String) = isSameConverted(expectedStatus, ticketsStatus)

    private def isSameScene(sceneString: String) = isSameConverted(place, sceneString)

    private def isSameConverted(left: String, right: String) = convertedString(left) == convertedString(right)

    private def convertedString(s: String) = s.toLowerCase.replaceAll("\\s", "")

  }


  implicit val ord: Ordering[LocalDate] = DateOrdering

  implicit object DateOrdering extends Ordering[LocalDate] {
    override def compare(x: LocalDate, y: LocalDate): Int = x.compareTo(y)
  }

}

object ServerApi {

  import akka.http.scaladsl.server.Directives._

  trait ApiRequest

  case object GetNextSaturdayPerformances extends ApiRequest


  def route(ref: ActorRef) =
    path("performanceInfo") {
      get {
        complete {

        }
      }
    }


  //  def requestHandler = {
  //    case HttpRequest(GET, Uri.Path("/performances/nextSaturday"), _, _, _) => GetNextSaturdayPerformances
  //  }

}
