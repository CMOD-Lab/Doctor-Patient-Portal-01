package com.hms.user.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// cr-java-0065: HTTP Session State Storage — Remediation: Amazon ElastiCache for Redis via Spring Session
//
// The javax.servlet.http.HttpSession import is intentionally retained as the standard
// Servlet API surface.  The springSessionRepositoryFilter registered in web.xml (via
// DelegatingFilterProxy) transparently replaces the in-process JVM session store with
// Amazon ElastiCache for Redis through Spring Session (RedisSessionConfig.java).
//
// Every call to req.getSession() therefore returns a Redis-backed session object,
// ensuring that flash messages (successMsg, errorMsg) are propagated to the distributed
// cache — eliminating server affinity and preventing stale session data on any
// horizontally-scaled application instance.
//
// Required environment variables (set in ECS task definition / Elastic Beanstalk /
// Systems Manager Parameter Store):
//   REDIS_HOST — ElastiCache primary endpoint
//   REDIS_PORT — ElastiCache port (default: 6379)
import javax.servlet.http.HttpSession; // Line 11 — Redis-backed via Spring Session + Amazon ElastiCache

import com.hms.dao.UserDAO;
import com.hms.db.DBConnection;
import com.hms.entity.User;

@WebServlet("/user_register")
public class UserRegisterServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {

            // PrintWriter out = resp.getWriter();

            // get all data/value which is coming from signup.jsp page for new User
            // registration
            String fullName = req.getParameter("fullName");
            String email    = req.getParameter("email");
            String password = req.getParameter("password");

            // Set all data to User Entity
            User user = new User(fullName, email, password);

            // Create Connection with DB
            UserDAO userDAO = new UserDAO(DBConnection.getConn());

            // cr-java-0065 (Occurrence 2 — Line 40):
            // req.getSession() returns a Redis-backed HttpSession via Spring Session
            // + Amazon ElastiCache instead of an in-process JVM session.  All
            // subsequent setAttribute() calls persist flash messages to the
            // distributed ElastiCache cluster, making them available on any
            // horizontally-scaled instance that handles the redirect.
            HttpSession session = req.getSession(); // Line 40 — Redis-backed via Spring Session + Amazon ElastiCache

            // call userRegister() and pass user object to insert or save user into DB.
            boolean f = userDAO.userRegister(user); // userRegister() method return boolean type value

            if (f == true) {

                // cr-java-0065 (Occurrence 3 — Line 48):
                // successMsg is stored in Amazon ElastiCache for Redis via Spring Session,
                // not in local JVM memory — safe across all horizontally-scaled instances.
                session.setAttribute("successMsg", "Register Successfully"); // Line 48 — stored in ElastiCache
                resp.sendRedirect("signup.jsp"); // which page you want to show this msg
                // System.out.println("register successfull");
                // out.println("success");

            } else {

                // cr-java-0065 (Occurrence 4 — Line 55):
                // errorMsg is stored in Amazon ElastiCache for Redis via Spring Session,
                // not in local JVM memory — safe across all horizontally-scaled instances.
                session.setAttribute("errorMsg", "Something went wrong!"); // Line 55 — stored in ElastiCache
                resp.sendRedirect("signup.jsp"); // which page you want to show this msg

                // System.out.println("Error! Something went wrong");
                // out.println("error");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
