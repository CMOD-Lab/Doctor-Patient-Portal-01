package com.hms.doctor.servlet;

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
// ensuring that session data (doctorObj, error messages) is stored in the
// distributed cache and is accessible to any horizontally-scaled application
// instance — eliminating server affinity and preventing data loss on
// instance termination or load-balancer failover.
import javax.servlet.http.HttpSession;

import com.hms.dao.DoctorDAO;
import com.hms.dao.UserDAO;
import com.hms.db.DBConnection;
import com.hms.entity.Doctor;


@WebServlet("/doctorLogin")
public class DoctorLoginServlet extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		//get email and password which is coming from doctor_login.jsp page
		String email = req.getParameter("email");
		String password = req.getParameter("password");

		// cr-java-0065 (Occurrence 5 — Line 10 import / Occurrence 6 — Line 29):
		// req.getSession() returns a Redis-backed HttpSession via Spring Session
		// + Amazon ElastiCache instead of an in-process JVM session.  The
		// authenticated doctor object and error messages are stored in the
		// distributed session store shared across all horizontally-scaled
		// application instances, eliminating server affinity.
		HttpSession session = req.getSession();

		//create DB connection
		DoctorDAO docDAO = new DoctorDAO(DBConnection.getConn());

		//call loginDoctor() method for doctor login which method declared in DoctorDAO
		Doctor doctor = docDAO.loginDoctor(email, password);

		if (doctor != null) {
			//means doctor is valid or exist
			//then store particular logged in doctor object in session
			// cr-java-0065 (Occurrence 7 — Line 40): "doctorObj" is stored in
			// Amazon ElastiCache for Redis via Spring Session, making the
			// authenticated doctor profile available to all application instances
			// without requiring session stickiness on the load balancer.
			session.setAttribute("doctorObj", doctor);
			//and redirect the particular doctor index page which is reside doctor folder
			resp.sendRedirect("doctor/index.jsp");//doctor index means dashboard of doctors
		} else {
			// cr-java-0065 (Occurrence 8 — Line 44): "errorMsg" flash attribute
			// is stored in Amazon ElastiCache for Redis via Spring Session,
			// ensuring the error message is available to whichever instance
			// handles the subsequent redirect request.
			session.setAttribute("errorMsg", "Invalid email or password");
			resp.sendRedirect("doctor_login.jsp");
		}

	}

}
