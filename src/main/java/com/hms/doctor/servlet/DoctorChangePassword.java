package com.hms.doctor.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// cr-java-0065: HTTP Session State Storage
// HttpSession is retained as the standard Servlet API surface.  The
// springSessionRepositoryFilter registered in web.xml (via
// DelegatingFilterProxy) transparently replaces the in-process JVM session
// store with Amazon ElastiCache for Redis through Spring Session.  Every
// call to req.getSession() therefore returns a Redis-backed session object,
// ensuring that session data (flash messages, doctor context) is stored in
// the distributed cache and is accessible to any horizontally-scaled
// application instance — eliminating server affinity and preventing data
// loss on instance termination or load-balancer failover.
import javax.servlet.http.HttpSession;

import com.hms.dao.DoctorDAO;
import com.hms.db.DBConnection;

@WebServlet("/doctorChangePassword")
public class DoctorChangePassword extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		int doctorId = Integer.parseInt(req.getParameter("doctorId"));
		String newPassword = req.getParameter("newPassword");
		String oldPassword = req.getParameter("oldPassword");

		DoctorDAO doctorDAO = new DoctorDAO(DBConnection.getConn());

		// cr-java-0065: req.getSession() returns a Redis-backed HttpSession
		// (via Spring Session + Amazon ElastiCache) instead of an in-process
		// session, ensuring flash messages are stored in the distributed session
		// store shared across all horizontally-scaled application instances.
		HttpSession session = req.getSession();

		if (doctorDAO.checkOldPassword(doctorId, oldPassword)) {

			if (doctorDAO.changePassword(doctorId, newPassword)) {

				// cr-java-0065: "successMsg" flash attribute is stored in ElastiCache for Redis via Spring Session.
				session.setAttribute("successMsg", "Password change successfully.");
				resp.sendRedirect("doctor/edit_profile.jsp");

			} else {

				// cr-java-0065: "errorMsg" flash attribute is stored in ElastiCache for Redis via Spring Session.
				session.setAttribute("errorMsg", "Something went wrong on server!");
				resp.sendRedirect("doctor/edit_profile.jsp");

			}

		} else {
			// cr-java-0065: "errorMsg" flash attribute is stored in ElastiCache for Redis via Spring Session.
			session.setAttribute("errorMsg", "Old Password not match");
			resp.sendRedirect("doctor/edit_profile.jsp");

		}
	}

}
