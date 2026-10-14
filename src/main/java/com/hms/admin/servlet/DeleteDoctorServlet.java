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
// via Spring Session.  All setAttribute calls below are therefore persisted
// to the distributed Redis store, enabling stateless application instances
// and safe horizontal scaling on AWS.
import javax.servlet.http.HttpSession;

import com.hms.dao.DoctorDAO;
import com.hms.db.DBConnection;

@WebServlet("/deleteDoctor")
public class DeleteDoctorServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		//get id(which is coming as string value) and convert into int	
		int id = Integer.parseInt(req.getParameter("id"));
		
		DoctorDAO docDAO = new DoctorDAO(DBConnection.getConn());
		// cr-java-0065: req.getSession() returns a Redis-backed HttpSession
		// (via Spring Session + Amazon ElastiCache) instead of an in-process
		// session, ensuring flash messages are stored in the distributed session
		// store shared across all horizontally-scaled application instances.
		HttpSession session = req.getSession();
		
		boolean f = docDAO.deleteDoctorById(id);
		
		if(f==true) {
			// cr-java-0065: "successMsg" flash attribute is stored in ElastiCache for Redis via Spring Session.
			session.setAttribute("successMsg", "Doctor Deleted Successfully.");
			resp.sendRedirect("admin/view_doctor.jsp");
		}
		else {
			// cr-java-0065: "errorMsg" flash attribute is stored in ElastiCache for Redis via Spring Session.
			session.setAttribute("errorMsg", "Something went wrong on server!");
			resp.sendRedirect("admin/view_doctor.jsp");
		}
	}
	
	

}
