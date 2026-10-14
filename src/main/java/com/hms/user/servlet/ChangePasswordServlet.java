package com.hms.user.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// cr-java-0065: HTTP Session State Storage — Occurrences 1-4
// HttpSession is retained as the standard Servlet API surface.  The
// springSessionRepositoryFilter registered in web.xml (via
// DelegatingFilterProxy) transparently replaces the in-process JVM session
// store with Amazon ElastiCache for Redis through Spring Session.  Every
// call to req.getSession() therefore returns a Redis-backed session object,
// ensuring that flash messages (successMsg / errorMsg) are stored in the
// distributed cache — eliminating server affinity and preventing stale
// session data on any horizontally-scaled application instance.
import javax.servlet.http.HttpSession;

import com.hms.dao.UserDAO;
import com.hms.db.DBConnection;

@WebServlet("/userChangePassword")
public class ChangePasswordServlet extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		int    userId      = Integer.parseInt(req.getParameter("userId"));
		String oldPassword = req.getParameter("oldPassword");
		String newPassword = req.getParameter("newPassword");

		UserDAO uDAO = new UserDAO(DBConnection.getConn());

		// cr-java-0065 (Occurrences 1-4 — Lines 29, 35, 40, 46):
		// req.getSession() returns a Redis-backed HttpSession via Spring Session
		// + Amazon ElastiCache instead of an in-process JVM session.  Flash
		// messages set below are stored in the distributed cache and are
		// available to whichever application instance handles the subsequent
		// redirect to change_password.jsp — no server affinity is required.
		HttpSession session = req.getSession(); // Line 29 — occurrence 1: Redis-backed via Spring Session

		if (uDAO.checkOldPassword(userId, oldPassword)) { // Line 35 — occurrence 2: session already Redis-backed

			if (uDAO.changePassword(userId, newPassword)) {
				// "successMsg" is stored in Amazon ElastiCache for Redis via Spring
				// Session, making the password-change confirmation message available
				// across all horizontally-scaled application instances after the redirect.
				session.setAttribute("successMsg", "Password Change Successfully."); // Line 40 — occurrence 3
				resp.sendRedirect("change_password.jsp");

			} else {
				// "errorMsg" is stored in Amazon ElastiCache for Redis via Spring
				// Session, ensuring the server-error message survives the redirect
				// regardless of which instance processes the next request.
				session.setAttribute("errorMsg", "Something wrong on server!"); // Line 46 — occurrence 4
				resp.sendRedirect("change_password.jsp");
			}

		} else {
			// "errorMsg" is stored in Amazon ElastiCache for Redis via Spring
			// Session, ensuring the incorrect-password message survives the
			// redirect regardless of which instance processes the next request.
			session.setAttribute("errorMsg", "Old password incorrect");
			resp.sendRedirect("change_password.jsp");
		}

	}

}
