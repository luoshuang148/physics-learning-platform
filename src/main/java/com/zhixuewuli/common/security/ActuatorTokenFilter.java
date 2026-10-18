package com.zhixuewuli.common.security;
import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.entity.User;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
/** Actuator has a separate handler mapping, so MVC interceptors cannot secure it. */
public final class ActuatorTokenFilter extends OncePerRequestFilter {
 private final JwtUtil jwt;private final UserRepository users;
 public ActuatorTokenFilter(JwtUtil jwt,UserRepository users){this.jwt=jwt;this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
  String path=PublicEndpointPolicy.path(request);
  if(PublicEndpointPolicy.isMonitoring(path)&&!path.equals("/actuator/health")) {
   String header=request.getHeader("Authorization");
   if(header!=null&&header.regionMatches(true,0,"Bearer ",0,7)) {
    String token=header.substring(7);
    try {
     if(jwt.validateToken(token)) {
      User user=users.findById(jwt.getUserIdFromToken(token)).orElse(null);
      if(user!=null&&Boolean.TRUE.equals(user.getActive())&&jwt.getTokenVersion(token)==user.getTokenVersion())
       SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(),null,
        List.of(new SimpleGrantedAuthority("ROLE_"+user.getUserType().name()))));
     }
    } catch(io.jsonwebtoken.JwtException|IllegalArgumentException invalid){SecurityContextHolder.clearContext();}
   }
  }
  chain.doFilter(request,response);
 }
}
