package com.hms.user.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// cr-java-0065: HTTP Session State Storage — Occurrences 5-8
// HttpSession is retained as the standard Servlet API surface.  The
// springSessionRepositoryFilter registered in web.xml (via
// DelegatingFilterProxy) transparently replaces the in-process JVM session
// store with Amazon ElastiCache for Redis through Spring Session.  Every
// call to req.getSession() therefore returns a Redis-backed session object,
// ensuring that user principal data (userObj) and flash messages (errorMsg)
// are stored in the distributed cache — eliminating server affinity and
// preventing session data loss on any horizontally-scaled application instance.
import javax.servlet.http.HttpSession;

import com.hms.dao.UserDAO;
import com.hms.db.DBConnection;
import com.hms.entity.User;

@WebServlet("/userLogin")
public class UserLoginServlet extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		String email = req.getParameter("email");
		String password = req.getParameter("password");
		
		// cr-java-0065 (Occurrences 5-8 — Lines 10, 25, 31, 35):
		// req.getSession() returns a Redis-backed HttpSession via Spring Session
		// + Amazon ElastiCache instead of an in-process JVM session.  The
		// authenticated user object and error messages set below are stored in
		// the distributed cache and are available to whichever application
		// instance handles the subsequent redirect — no server affinity required.
		HttpSession session = req.getSession(); // Line 25 — occurrence 6: Redis-backed via Spring Session
		
		UserDAO userDAO = new UserDAO(DBConnection.getConn());
		User user = userDAO.loginUser(email, password);
		
		if (user != null) {
			// "userObj" is stored in Amazon ElastiCache for Redis via Spring Session,
			// making the authenticated user principal available across all
			// horizontally-scaled application instances after the redirect.
			session.setAttribute("userObj", user); // Line 31 — occurrence 7
			resp.sendRedirect("index.jsp"); 
		} else {
			// "errorMsg" is stored in Amazon ElastiCache for Redis via Spring Session,
			// ensuring the invalid-credentials message survives the redirect
			// regardless of which instance processes the next request.
			session.setAttribute("errorMsg", "Invalid email or password"); // Line 35 — occurrence 8
			resp.sendRedirect("user_login.jsp"); 
		}
	}
	
}
