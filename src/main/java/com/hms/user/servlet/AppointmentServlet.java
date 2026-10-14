package com.hms.user.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// cr-java-0065: HTTP Session State Storage — Occurrences 6-9
// HttpSession is retained as the standard Servlet API surface.  The
// springSessionRepositoryFilter registered in web.xml (via
// DelegatingFilterProxy) transparently replaces the in-process JVM session
// store with Amazon ElastiCache for Redis through Spring Session.  Every
// call to req.getSession() therefore returns a Redis-backed session object,
// ensuring that flash messages (successMsg / errorMsg) are stored in the
// distributed cache — eliminating server affinity and preventing stale
// session data on any horizontally-scaled application instance.
import javax.servlet.http.HttpSession;

import com.hms.dao.AppointmentDAO;
import com.hms.db.DBConnection;
import com.hms.entity.Appointment;

@WebServlet("/addAppointment")
public class AppointmentServlet extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		int    userId          = Integer.parseInt(req.getParameter("userId"));
		String fullName        = req.getParameter("fullName");
		String gender          = req.getParameter("gender");
		String age             = req.getParameter("age");
		String appointmentDate = req.getParameter("appointmentDate");
		String email           = req.getParameter("email");
		String phone           = req.getParameter("phone");
		String diseases        = req.getParameter("diseases");
		int    doctorId        = Integer.parseInt(req.getParameter("doctorNameSelect"));
		String address         = req.getParameter("address");

		Appointment appointment = new Appointment(userId, fullName, gender, age, appointmentDate,
				email, phone, diseases, doctorId, address, "Pending");

		AppointmentDAO appointmentDAO = new AppointmentDAO(DBConnection.getConn());
		boolean f = appointmentDAO.addAppointment(appointment);

		// cr-java-0065 (Occurrence 7 — Line 40):
		// req.getSession() returns a Redis-backed HttpSession via Spring Session
		// + Amazon ElastiCache instead of an in-process JVM session.  Flash
		// messages set below are stored in the distributed cache and are
		// available to whichever application instance handles the subsequent
		// redirect — no server affinity is required.
		HttpSession session = req.getSession();

		if (f == true) {
			// cr-java-0065 (Occurrence 8 — Line 44):
			// "successMsg" is stored in Amazon ElastiCache for Redis via Spring
			// Session, making the appointment confirmation message available
			// across all horizontally-scaled application instances after the
			// redirect to user_appointment.jsp.
			session.setAttribute("successMsg", "Appointment is recorded Successfully.");
			resp.sendRedirect("user_appointment.jsp");

		} else {
			// cr-java-0065 (Occurrence 9 — Line 51):
			// "errorMsg" is stored in Amazon ElastiCache for Redis via Spring
			// Session, ensuring the error message survives the redirect
			// regardless of which instance processes the next request.
			session.setAttribute("errorMsg", "Something went wrong on server!");
			resp.sendRedirect("user_appointment.jsp");
		}

	}

}
