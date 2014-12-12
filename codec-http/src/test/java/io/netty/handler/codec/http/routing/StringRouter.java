package io.netty.handler.codec.http.routing;

import io.netty.handler.codec.http.HttpMethod;

public class StringRouter extends Router<HttpMethod, String, StringRouter> {
  protected StringRouter getThis() { return this; }

  protected HttpMethod CONNECT() { return HttpMethod.CONNECT; }
  protected HttpMethod DELETE()  { return HttpMethod.DELETE; }
  protected HttpMethod GET()     { return HttpMethod.GET; }
  protected HttpMethod HEAD()    { return HttpMethod.HEAD; }
  protected HttpMethod OPTIONS() { return HttpMethod.OPTIONS; }
  protected HttpMethod PATCH()   { return HttpMethod.PATCH; }
  protected HttpMethod POST()    { return HttpMethod.POST; }
  protected HttpMethod PUT()     { return HttpMethod.PUT; }
  protected HttpMethod TRACE()   { return HttpMethod.TRACE; }

  public static final StringRouter router = new StringRouter()
    .GET      ("/articles",             "index")
    .GET      ("/articles/:id",         "show")
    .GET      ("/articles/:id/:format", "show")
    .GET_FIRST("/articles/new",         "new")
    .POST     ("/articles",             "post")
    .PATCH    ("/articles/:id",         "patch")
    .DELETE   ("/articles/:id",         "delete")
    .ANY      ("/anyMethod",            "anyMethod")
    .GET      ("/download/:*",          "download")
    .notFound("404");

  static {
      // Visualize the routes
      System.out.println(router.toString());
  }

  static abstract class Action {}
  static class Index extends Action {}
  static class Show  extends Action {}
}
