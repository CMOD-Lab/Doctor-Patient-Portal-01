package com.hms.admin.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// cr-java-0065: HttpSession is retained as the API surface, but the
// springSessionRepositoryFilter (registered in web.xml) transparently
// replaces the in-process session store with Amazon ElastiCache for Redis
// via Spring Session.  All removeAttribute / setAttribute calls below are
// therefore applied to the distributed Redis store, enabling stateless
// application instances and safe horizontal scaling on AWS.
import javax.servlet.http.HttpSession;

@WebServlet("/adminLogout")
public class AdminLogoutServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		// cr-java-0065: req.getSession() returns a Redis-backed HttpSession
		// (via Spring Session + Amazon ElastiCache) instead of an in-process
		// session, ensuring the logout operation removes the attribute from the
		// distributed session store shared across all application instances.
		// get session means get "adminObj" and remove it, logout done!
		HttpSession session = req.getSession();
		// cr-java-0065: "adminObj" is removed from ElastiCache for Redis via Spring Session.
		session.removeAttribute("adminObj");
		// cr-java-0065: "successMsg" flash attribute is stored in ElastiCache for Redis via Spring Session.
		//show message after logout
		session.setAttribute("successMsg", "Admin Logout Successfully");
		resp.sendRedirect("admin_login.jsp");
		
		
		
	}

	
}
