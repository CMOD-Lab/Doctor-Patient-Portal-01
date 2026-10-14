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
// via Spring Session.  All setAttribute / getAttribute calls below are
// therefore persisted to the distributed Redis store, enabling stateless
// application instances and safe horizontal scaling on AWS.
import javax.servlet.http.HttpSession;

import com.hms.entity.User;

@WebServlet("/adminLogin")
public class AdminLoginServlet extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		try {
			
			//create one static Admin for this project
			String email = req.getParameter("email");
			String password = req.getParameter("password");
			
			// cr-java-0065: req.getSession() returns a Redis-backed HttpSession
			// (via Spring Session + Amazon ElastiCache) instead of an in-process
			// session, ensuring session data survives instance restarts and is
			// shared across all horizontally-scaled application instances.
			HttpSession session = req.getSession();
			
			//logic for a static Admin
			if ("admin@gmail.com".equals(email) && "admin".equals(password)) {
				
				//if "adminObj" obj available then give the access of admin page, 
				//otherwise "adminObj" is not present in obj then others user is login(which is not admin). so dont give him the access of Admin.
				//the below line specially check the admin is log in or not! "adminObj" object is available that means admin is log in.
				// cr-java-0065: "adminObj" is stored in ElastiCache for Redis via Spring Session.
				session.setAttribute("adminObj", new User());
				resp.sendRedirect("admin/index.jsp");
			}
			else {
				// cr-java-0065: "errorMsg" flash attribute is stored in ElastiCache for Redis via Spring Session.
				session.setAttribute("errorMsg", "Invalid Username or Password.");
				resp.sendRedirect("admin_login.jsp");
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
}
