package com.manonthegithub

import org.scalatest.WordSpec

/**
  * Created by Kirill on 19/02/2017.
  */
class MailSenderSpec extends WordSpec {


  "MailSender" should {

    "send message" ignore {

      val singleRecip = Array("mytrashpostbox@gmail.com")
      val multRecips = singleRecip :+ "mydealmailbox@gmail.com"

      val sender = MailSender("smtp.yandex.ru", 465, "robot@bookpleasure.ru", "1qaz@WSX")

      println(System.currentTimeMillis() / 1000)
      sender.send("Hello", "It's me!", singleRecip)
      println(System.currentTimeMillis() / 1000)
      sender.send("Hello2", "It's me again!", multRecips)
      println(System.currentTimeMillis() / 1000)
      sender.close
    }

  }

}
