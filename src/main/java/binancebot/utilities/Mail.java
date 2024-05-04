package binancebot.utilities;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import binancebot.FindBooster;

public abstract class Mail {

	private static Session session;
	
	public static boolean boosterInfoMail() {
		return Boolean.valueOf(Env.getProperty("sendEmails.boosterInfo", "false"));
	}
	
	public static boolean buyOrderMails() {
		return Boolean.valueOf(Env.getProperty("sendEmails.buyOrderMails", "false"));
	}
	
	public static boolean sellOrderMails() {
		return Boolean.valueOf(Env.getProperty("sendEmails.sellOrderMails", "false"));
	}
	
	public static boolean cancelOrderMails() {
		return Boolean.valueOf(Env.getProperty("sendEmails.cancelOrderMails", "false"));
	}
	
	/** send email, subject and body can be defined in code */
	public static void send(String subject) {
		send(subject, "");
	}

	/** send email, subject and body can be defined in code */
	public static void send(String subject, String body) {

		if (FindBooster.sendmails) {

			String to = Env.getProperty("mail.smtp.receiver");

			// get system properties
			Properties properties = System.getProperties();
			properties.put("mail.transport.protocol", Env.getProperty("mail.transport.protocol"));
			properties.put("mail.smtp.host", Env.getProperty("mail.smtp.host"));
			properties.put("mail.smtp.port", Env.getProperty("mail.smtp.port"));
			properties.put("mail.smtp.auth", Env.getProperty("mail.smtp.auth"));
			properties.put("mail.smtp.user", Env.getProperty("mail.smtp.sending.user"));
			properties.put("mail.smtp.password", Env.getProperty("mail.smtp.password"));
			properties.put("mail.smtp.starttls.enable", Env.getProperty("mail.smtp.starttls.enable"));

			// create GMX session, if required
			try {
				if (session == null) {
					session = Session.getInstance(properties, new Authenticator() {
						@Override
						protected PasswordAuthentication getPasswordAuthentication() {
							return new PasswordAuthentication(properties.getProperty("mail.smtp.user"),
									properties.getProperty("mail.smtp.password"));
						}
					});
				}
				// send message
				if (session != null) {

					// Create a default MimeMessage object.
					MimeMessage message = new MimeMessage(session);

					// Set From: header field of the header.
					message.setFrom(new InternetAddress(properties.getProperty("mail.smtp.user")));

					// Set To: header field of the header.
					message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

					// Set Subject: header field
					message.setSubject(subject);

					// Now set the actual message
					message.setText(body);

					// Send message
					Transport.send(message);

				} else {
					Log.log("Session null: Could not send email to " + to);
				}

			} catch (Exception e) {
				Log.log("Mail Sending failed! Exception: " +e.toString());
			}

		}
	}

}
