package com.manonthegithub

import javax.mail.{Address, Session}
import javax.mail.internet.{InternetAddress, MimeMessage}


/**
  * Created by Kirill on 10/02/2017.
  */
case class MailSender(host: String, port: Int, from: String, pass: String) {

  private val props = System.getProperties
  props.setProperty("mail.smtp.host", host)
  props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
  props.setProperty("mail.smtp.socketFactory.port", port.toString)
  props.setProperty("mail.smtp.port", port.toString)
  props.setProperty("mail.smtp.auth", true.toString)
  props.setProperty("mail.smtp.starttls.enable", true.toString)
  props.setProperty("mail.smtp.connectiontimeout", 5000.toString)
  props.setProperty("mail.smtp.timeout", 5000.toString)


  private val session = Session.getDefaultInstance(props)
  private val transport = session.getTransport

  def connect = transport.connect(from, pass)


  def send(subject: String, text: String, addresses: Array[String]) = try {
    if (!transport.isConnected) {
      transport.connect(from, pass)
    }
    val message = new MimeMessage(session)
    message.setFrom(new InternetAddress(from))
    message.setSubject(subject)
    message.setText(text)
    transport.sendMessage(message, addresses.map[Address, Array[Address]](new InternetAddress(_)))
  } catch {
    case _ => close
  }

  def close = transport.close

}