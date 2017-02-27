package com.manonthegithub

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import org.scalatest.{Matchers, WordSpec}


/**
  * Created by Kirill on 09/02/2017.
  */
class MainSpec extends WordSpec with Matchers {

  import MainApp._

  "date" should {
    "be right" in {
      dateForNewTicketsAvailable(LocalDate.of(2017, 2, 9)).toString should be("2017-05-06")
    }

    "be right 2" in {
      dateForNewTicketsAvailable(LocalDate.of(2017, 2, 9)).minus(1, ChronoUnit.WEEKS).toString should be("2017-04-29")
    }
  }

}
