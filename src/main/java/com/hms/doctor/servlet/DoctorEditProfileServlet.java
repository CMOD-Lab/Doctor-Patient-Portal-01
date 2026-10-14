package com.hms.doctor.servlet;

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
// ensuring that session data (flash messages, doctor profile object) is
// stored in the distributed cache and is accessible to any
// horizontally-scaled application instance — eliminating server affinity
// and preventing data loss on instance termination or load-balancer failover.
import javax.servlet.http.HttpSession;

import com.hms.dao.DoctorDAO;
import com.hms.db.DBConnection;
import com.hms.entity.Doctor;

@WebServlet("/doctorEditProfile")
public class DoctorEditProfileServlet extends HttpServlet {

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		try {

			// get all data which is coming from doctor.jsp doctor details
			String fullName = req.getParameter("fullName");
			String dateOfBirth = req.getParameter("dateOfBirth");
			String qualification = req.getParameter("qualification");
			String specialist = req.getParameter("specialist");
			String email = req.getParameter("email");
			String phone = req.getParameter("phone");
			//String password = req.getParameter("password");

			int id = Integer.parseInt(req.getParameter("doctorId"));

			Doctor doctor = new Doctor(id, fullName, dateOfBirth, qualification, specialist, email, phone, "");

			DoctorDAO docDAO = new DoctorDAO(DBConnection.getConn());

			boolean f = docDAO.editDoctorProfile(doctor);

			// cr-java-0065 (Occurrence 1 — Line 42): req.getSession() returns a
			// Redis-backed HttpSession via Spring Session + Amazon ElastiCache
			// instead of an in-process JVM session.  Session data is stored in
			// the distributed cache shared across all horizontally-scaled
			// application instances, eliminating server affinity.
			HttpSession session = req.getSession();

			if (f == true) {
				Doctor updateDoctorObj = docDAO.getDoctorById(id);
				// cr-java-0065 (Occurrence 2 — Line 46): "successMsgForD" flash
				// attribute is stored in Amazon ElastiCache for Redis via Spring
				// Session, making it available to any application instance that
				// serves the subsequent redirect request.
				session.setAttribute("successMsgForD", "Doctor update Successfully");
				// cr-java-0065 (Occurrence 3 — Line 47): "doctorObj" is stored in
				// Amazon ElastiCache for Redis via Spring Session, overriding the
				// old session value with the newly updated doctor profile so that
				// every instance sees the latest state.
				session.setAttribute("doctorObj", updateDoctorObj); // over ride or update old session value to new updated doctor value.
				resp.sendRedirect("doctor/edit_profile.jsp");

			} else {
				// cr-java-0065 (Occurrence 4 — Line 51): "errorMsgForD" flash
				// attribute is stored in Amazon ElastiCache for Redis via Spring
				// Session, ensuring the error message survives the redirect even
				// when a different instance handles the follow-up request.
				session.setAttribute("errorMsgForD", "Something went wrong on server!");
				resp.sendRedirect("doctor/edit_profile.jsp");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
