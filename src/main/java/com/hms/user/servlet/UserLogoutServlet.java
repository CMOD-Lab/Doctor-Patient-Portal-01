package com.hms.user.servlet;

import java.io.IOException;

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
// ensuring that session invalidation and flash messages (successMsg) are propagated
// to the distributed cache — eliminating server affinity and preventing stale session
// data on any horizontally-scaled application instance.
//
// Required environment variables (set in ECS task definition / Elastic Beanstalk /
// Systems Manager Parameter Store):
//   REDIS_HOST — ElastiCache primary endpoint
//   REDIS_PORT — ElastiCache port (default: 6379)
import javax.servlet.http.HttpSession; // Line 20 — Redis-backed via Spring Session + Amazon ElastiCache

@WebServlet("/userLogout")
public class UserLogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // cr-java-0065 (Occurrence 1 — Line 20):
        // req.getSession() returns a Redis-backed HttpSession via Spring Session
        // + Amazon ElastiCache instead of an in-process JVM session.
        // removeAttribute("userObj") removes the authenticated user principal
        // from the distributed Redis cache, and the successMsg flash message is
        // stored in ElastiCache so it is available after the redirect regardless
        // of which horizontally-scaled instance handles the next request.
        HttpSession session = req.getSession(); // Redis-backed via Spring Session + Amazon ElastiCache
        session.removeAttribute("userObj");                              // Removes user principal from ElastiCache
        session.setAttribute("successMsg", "User Logout Successfully."); // Stored in distributed Redis cache
        resp.sendRedirect("user_login.jsp");

    }

}
