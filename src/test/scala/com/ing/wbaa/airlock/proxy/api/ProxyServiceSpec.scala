package com.ing.wbaa.airlock.proxy.api

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.{ ActorMaterializer, Materializer }
import com.ing.wbaa.airlock.proxy.data._
import org.scalatest.{ DiagrammedAssertions, FlatSpec }

import scala.concurrent.{ ExecutionContext, Future }

class ProxyServiceSpec extends FlatSpec with DiagrammedAssertions with ScalatestRouteTest {

  private trait ProxyServiceMock extends ProxyService {
    override implicit def system: ActorSystem = ActorSystem.create("test-system")

    override implicit def executionContext: ExecutionContext = system.dispatcher

    implicit def materializer: Materializer = ActorMaterializer()

    override def executeRequest(request: HttpRequest, userSTS: User, s3request: S3Request): Future[HttpResponse] =
      Future(HttpResponse(status = StatusCodes.OK))

    override def areCredentialsActive(awsRequestCredential: AwsRequestCredential): Future[Option[User]] = Future(
      Some(User(UserName("okUser"), Set(UserGroup("okGroup")), AwsAccessKey("accesskey"), AwsSecretKey("secretkey")))
    )

    override def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = true

    override def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean = true

    override protected[this] def handlePostRequestActions(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request, userSTS: User): Unit = ()
  }

  private def testRequest(accessKey: String = "okAccessKey", path: String = "/okBucket") = HttpRequest(
    headers = List(
      RawHeader("authorization", s"AWS $accessKey:bla"),
      RawHeader("x-amz-security-token", "okSessionToken"),
      `Remote-Address`(RemoteAddress(InetAddress.getByName("6.7.8.9"), Some(1234)))
    ),
    uri = Uri(
      scheme = "http",
      authority = Authority(Uri.Host("host"), 3456),
      path = Uri.Path(path))
  )

  "A proxy service" should "Successfully execute a request" in {
    testRequest() ~> new ProxyServiceMock {}.proxyServiceRoute ~> check {
      assert(status == StatusCodes.OK)
    }
  }

  it should "return an accessDenied when the user credentials cannot be authenticated" in {
    testRequest("notOkAccessKey") ~> new ProxyServiceMock {
      override def areCredentialsActive(awsRequestCredential: AwsRequestCredential): Future[Option[User]] = Future(None)
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Forbidden)
      val response = responseAs[String].replaceAll("\\s", "")
      assert(response == "<Error><Code>AccessDenied</Code><Message>AccessDenied</Message><Resource></Resource><RequestId></RequestId></Error>")
    }
  }

  it should "return a serviceUnavailable when an exception occurs in authentication" in {
    testRequest() ~> new ProxyServiceMock {
      override def areCredentialsActive(awsRequestCredential: AwsRequestCredential): Future[Option[User]] = Future(throw new Exception("BOOM"))
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.InternalServerError)
      val response = responseAs[String].replaceAll("\\s", "")
      assert(response == "<Error><Code>ServiceUnavailable</Code><Message>Reduceyourrequestrate.</Message>" +
        "<Resource></Resource><RequestId></RequestId></Error>")
    }
  }

  it should "return an accessDenied when user is not authorized" in {
    testRequest() ~> new ProxyServiceMock {
      override def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = false
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Forbidden)
      val response = responseAs[String].replaceAll("\\s", "")
      assert(response == "<Error><Code>AccessDenied</Code><Message>AccessDenied</Message><Resource></Resource><RequestId></RequestId></Error>")
    }
  }

  it should "return an accessDenied when user is not authenticated" in {
    testRequest() ~> new ProxyServiceMock {
      override def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean = false
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Forbidden)
      val response = responseAs[String].replaceAll("\\s", "")
      assert(response == "<Error><Code>AccessDenied</Code><Message>AccessDenied</Message><Resource></Resource><RequestId></RequestId></Error>")
    }
  }
}
