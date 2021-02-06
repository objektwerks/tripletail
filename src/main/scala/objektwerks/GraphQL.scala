package objektwerks

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, OK}
import akka.http.scaladsl.server.Directives

import sangria.ast.Document
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.sprayJson.{SprayJsonInputUnmarshallerJObject, SprayJsonResultMarshaller}
import sangria.parser.QueryParser

import spray.json.{JsObject, JsString, JsValue}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

object GraphQL {
  def apply()(implicit executor: ExecutionContextExecutor): GraphQL = new GraphQL()
}

class GraphQL(implicit executor: ExecutionContextExecutor) extends Directives with UserSchema with UserJsonSupport {
  def parseQuery(queryJsValue: JsValue): (String, Option[String], JsObject) = {
    val JsObject(fields) = queryJsValue
    val JsString(query) = fields("query")
    val operationName = fields.get("operationName") collect {
      case JsString(op) => op
    }
    val variables = fields.get("variables") match {
      case Some(jsObject: JsObject) => jsObject
      case _ => JsObject.empty
    }
    (query, operationName, variables)
  }

  def parseQuery(query: String): Try[Document] = QueryParser.parse(query)

  def executeQuery(query: Document,
                   operationName: Option[String],
                   variables: JsObject): Future[(StatusCode, JsValue)] =
    Executor
      .execute(UserSchema, query, UserStore(), variables = variables, operationName = operationName)
      .map( OK -> _ )
      .recover {
        case error: QueryAnalysisError => BadRequest -> error.resolveError
        case error: ErrorWithResolver => InternalServerError -> error.resolveError
      }
}