package com.ing.wbaa.airlock.proxy.api

import akka.Done
import akka.http.scaladsl.model._
import com.ing.wbaa.airlock.proxy.config.{ AtlasSettings, KafkaSettings }
import com.ing.wbaa.airlock.proxy.data.{ LineageResponse, S3Request, User }

import scala.concurrent.Future

trait PostRequestActions {
  protected[this] def atlasSettings: AtlasSettings

  protected[this] def kafkaSettings: KafkaSettings

  protected[this] def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress): Future[LineageResponse]

  protected[this] def emitEvent(s3Request: S3Request, method: HttpMethod, principalId: String): Future[Done]

  def createAtlasLineage(response: HttpResponse, httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress): Unit =
    if (atlasSettings.atlasEnabled && (response.status == StatusCodes.OK || response.status == StatusCodes.NoContent))
      // delete on AWS response 204
      createLineageFromRequest(httpRequest, userSTS, clientIPAddress)

  protected[this] def createBucketNotification(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request,
      userSTS: User): Future[Done] =
    httpRequest.method match {
      case HttpMethods.POST | HttpMethods.PUT | HttpMethods.DELETE if kafkaSettings.kafkaEnabled && (response.status == StatusCodes.OK || response.status == StatusCodes.NoContent) =>
        emitEvent(s3Request, httpRequest.method, userSTS.userName.value)
      case _ => Future.successful(Done)
    }

  protected[this] def handlePostRequestActions(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request, userSTS: User): Unit = {
    createAtlasLineage(response, httpRequest, userSTS, s3Request.clientIPAddress.get)
    createBucketNotification(response, httpRequest, s3Request, userSTS)
  }

}
