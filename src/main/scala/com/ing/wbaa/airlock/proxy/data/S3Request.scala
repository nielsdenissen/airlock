package com.ing.wbaa.airlock.proxy.data

import akka.http.scaladsl.model.{ HttpMethod, RemoteAddress }
import akka.http.scaladsl.model.Uri.Path
import com.typesafe.scalalogging.LazyLogging

/**
 * @param credential
 * @param s3BucketPath     A None for bucket means this is an operation not targeted to a specific bucket (e.g. list buckets)
 * @param s3Object
 * @param accessType The access type for this request, write includes actions like write/update/delete
 *
 */
case class S3Request(
    credential: AwsRequestCredential,
    s3BucketPath: Option[String],
    s3Object: Option[String],
    accessType: AccessType,
    clientIPAddress: Option[RemoteAddress] = None,
    headerIPs: Option[HeaderIPs] = None
)

object S3Request extends LazyLogging {
  def extractObject(pathString: String): Option[String] =
    if (pathString.endsWith("/") || pathString.split("/").length < 3) {
      None
    } else {
      Some(pathString.split("/").last)
    }

  def apply(credential: AwsRequestCredential, path: Path, httpMethod: HttpMethod): S3Request = {

    val pathString = path.toString()
    val s3path = if (path.length > 1) { Some(pathString) } else { None }
    val s3Object = extractObject(pathString)

    val accessType = httpMethod.value match {
      case "GET"    => Read
      case "HEAD"   => Head
      case "PUT"    => Write
      case "POST"   => Write
      case "DELETE" => Delete
      case _ =>
        logger.debug("HttpMetchod not supported")
        NoAccess
    }

    S3Request(credential, s3path, s3Object, accessType)
  }
}
