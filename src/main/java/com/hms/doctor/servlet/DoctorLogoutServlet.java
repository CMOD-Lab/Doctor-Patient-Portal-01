package com.hms.doctor.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// cr-java-0065: HTTP Session State Storage — Occurrences 9-10
// HttpSession is retained as the standard Servlet API surface.  The
// springSessionRepositoryFilter registered in web.xml (via
// DelegatingFilterProxy) transparently replaces the in-process JVM session
// store with Amazon ElastiCache for Redis through Spring Session.  Every
// call to req.getSession() therefore returns a Redis-backed session object,
// ensuring that session invalidation and logout messages are handled in the
// distributed cache — eliminating server affinity and preventing stale
// session data on any horizontally-scaled application instance.
import javax.servlet.http.HttpSession;

@WebServlet("/doctorLogout")
public class DoctorLogoutServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		// cr-java-0065 (Occurrence 9 — Line 10 import / Occurrence 10 — Line 18):
		// req.getSession() returns a Redis-backed HttpSession via Spring Session
		// + Amazon ElastiCache instead of an in-process JVM session.  Removing
		// "doctorObj" and setting the success message operates on the distributed
		// session store, so the logout is effective across all horizontally-scaled
		// application instances — no stale authenticated sessions remain on any
		// node after the doctor logs out.
		HttpSession session = req.getSession();
		// cr-java-0065: "doctorObj" is removed from Amazon ElastiCache for Redis
		// via Spring Session, invalidating the doctor's authenticated state across
		// all application instances simultaneously.
		session.removeAttribute("doctorObj");
		// cr-java-0065: "successMsg" flash attribute is stored in Amazon
		// ElastiCache for Redis via Spring Session, ensuring the logout
		// confirmation message is available to whichever instance handles
		// the subsequent redirect to doctor_login.jsp.
		session.setAttribute("successMsg", "Doctor Logout Successfully.");
		resp.sendRedirect("doctor_login.jsp");
	}

}
