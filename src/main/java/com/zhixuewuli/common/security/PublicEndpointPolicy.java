package com.zhixuewuli.common.security;

/** Exact public API policy shared by all MVC authentication decisions. */
public final class PublicEndpointPolicy {
 private PublicEndpointPolicy() {}
 public static String path(jakarta.servlet.http.HttpServletRequest request){
  String uri=request.getRequestURI(),context=request.getContextPath();
  if(uri==null)return "";return context!=null&&uri.startsWith(context)?uri.substring(context.length()):uri;
 }
 public static boolean isPublic(String path,boolean docsEnabled) {
  return path.equals("/auth/login")||path.equals("/auth/register")||path.equals("/actuator/health")
   ||path.startsWith("/videos/")||(docsEnabled&&(path.equals("/v3/api-docs")||path.startsWith("/v3/api-docs/")
   ||path.equals("/swagger-ui.html")||path.startsWith("/swagger-ui/")||path.startsWith("/webjars/")));
 }
 public static boolean isMonitoring(String path){return path.equals("/actuator")||path.startsWith("/actuator/");}
}
