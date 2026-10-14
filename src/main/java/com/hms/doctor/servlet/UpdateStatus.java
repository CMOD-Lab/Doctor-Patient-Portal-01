package com.hms.doctor.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// cr-java-0065: HTTP Session State Storage — Occurrences 2-5
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

@WebServlet("/updateStatus")
public class UpdateStatus extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		try {

			int    id       = Integer.parseInt(req.getParameter("id"));
			int    doctorId = Integer.parseInt(req.getParameter("doctorId"));
			String comment  = req.getParameter("comment");

			AppointmentDAO appDAO = new AppointmentDAO(DBConnection.getConn());
			boolean f = appDAO.updateDrAppointmentCommentStatus(id, doctorId, comment);

			// cr-java-0065 (Occurrence 3 — Line 30):
			// req.getSession() returns a Redis-backed HttpSession via Spring Session
			// + Amazon ElastiCache instead of an in-process JVM session.  Flash
			// messages set below are stored in the distributed cache and are
			// available to whichever application instance handles the subsequent
			// redirect — no server affinity is required.
			HttpSession session = req.getSession();

			if (f == true) {
				// cr-java-0065 (Occurrence 4 — Line 34):
				// "successMsg" is stored in Amazon ElastiCache for Redis via Spring
				// Session, making the confirmation message available across all
				// horizontally-scaled application instances after the redirect.
				session.setAttribute("successMsg", "Comment updated");
				resp.sendRedirect("doctor/patient.jsp");

			} else {
				// cr-java-0065 (Occurrence 5 — Line 39):
				// "errorMsg" is stored in Amazon ElastiCache for Redis via Spring
				// Session, ensuring the error message survives the redirect
				// regardless of which instance processes the next request.
				session.setAttribute("errorMsg", "Something went wrong on server!");
				resp.sendRedirect("doctor/patient.jsp");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
