package org.enso.languageserver.text

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import org.enso.languageserver.data.{CapabilityRegistration, ClientId}
import org.enso.languageserver.filemanager.{
  FileAttributes,
  FileEventKinds,
  FileSystemFailure,
  Path
}
import org.enso.languageserver.session.JsonSession
import org.enso.polyglot.runtime.Runtime.Api.ExpressionId
import org.enso.text.editing.model.{IdMap, Span, TextEdit}

import java.util.UUID

object TextProtocol {

  /** Requests the language server to open an in-memory buffer on behalf of a
    * given user.
    *
    * @param rpcSession the client opening the file.
    * @param path the file path.
    */
  case class OpenBuffer(rpcSession: JsonSession, path: Path)

  /** Requests the language server to open a file on behalf of a given user.
    *
    * @param rpcSession the client opening the file.
    * @param path the file path.
    */
  case class OpenFile(rpcSession: JsonSession, path: Path)

  /** Sent by the server in response to [[OpenFile]]
    *
    * @param result either a file system failure, or successful opening data.
    */
  case class OpenFileResponse(result: Either[FileSystemFailure, OpenFileResult])

  /** The data carried by a successful file open operation.
    *
    * @param buffer file contents and current version.
    * @param writeCapability a write capability that could have been
    *                        automatically granted.
    */
  case class OpenFileResult(
    buffer: Buffer,
    writeCapability: Option[CapabilityRegistration]
  )

  /** Requests the language server to close a file on behalf of a given user.
    *
    * @param clientId the client closing the file.
    * @param path the file path.
    */
  case class CloseFile(clientId: ClientId, path: Path)

  /** Signals file close status.
    */
  sealed trait CloseFileResult

  /** Confirms that a file was successfully closed.
    */
  case object FileClosed extends CloseFileResult

  /** Signals that a file wasn't opened.
    */
  case object FileNotOpened

  /** Requests the language server to apply a series of edits to the buffer.
    *
    * @param clientId the client requesting edits.
    * @param edit a diff describing changes made to a file
    * @param execute whether to execute the program after applying the edits
    * @param idMap external identifiers
    */
  case class ApplyEdit(
    clientId: Option[ClientId],
    edit: FileEdit,
    execute: Boolean,
    idMap: Option[IdMap]
  )

  /** Signals the result of applying a series of edits.
    */
  sealed trait ApplyEditResult

  /** Signals that all edits were applied successfully.
    */
  case object ApplyEditSuccess extends ApplyEditResult

  /** A base trait for all failures regarding editing.
    */
  sealed trait ApplyEditFailure extends ApplyEditResult

  /** Requests the language server to substitute the value of an expression.
    *
    * @param clientId the client requesting to set the expression value.
    * @param expressionId the expression to update
    * @param path a path of a file
    * @param edit a diff describing changes made to a file
    * @param oldVersion the current version of a buffer
    * @param newVersion the version of a buffer after applying all edits
    */
  case class ApplyExpressionValue(
    clientId: ClientId,
    expressionId: ExpressionId,
    path: Path,
    edit: TextEdit,
    oldVersion: TextApi.Version,
    newVersion: TextApi.Version
  )

  /** Signals that the client doesn't hold write lock to the buffer.
    */
  case object WriteDenied extends ApplyEditFailure

  /** Signals that validation has failed for a series of edits.
    *
    * @param msg a validation message
    */
  case class TextEditValidationFailed(msg: String) extends ApplyEditFailure

  /** Signals that version provided by a client doesn't match to the version
    * computed by the server.
    *
    * @param clientVersion a version send by the client
    * @param serverVersion a version computed by the server
    */
  case class TextEditInvalidVersion(
    clientVersion: TextApi.Version,
    serverVersion: TextApi.Version
  ) extends ApplyEditFailure

  /** A notification sent by the Language Server, notifying a client about
    * edits made by the write lock holder.
    *
    * @param changes a series of edits
    */
  case class TextDidChange(changes: List[FileEdit])

  /** A notification sent by the Language Server, notifying a client about
    * a successful auto-save action.
    *
    * @param path path to the saved file
    */
  case class FileAutoSaved(path: Path)

  /** A notification sent by the Language Server, notifying a client that the
    * file was modified on disk.
    *
    * @param path path to the file
    */
  case class FileModifiedOnDisk(path: Path)

  /** A notification sent by the Language Server, notifying a client about
    * a file event after reloading the buffer to sync with file system
    *
    * @param path path to the file
    * @param kind file event kind
    * @param attributes file attributes
    */
  case class FileEvent(
    path: Path,
    kind: FileEventKinds.FileEventKind,
    attributes: Option[FileAttributes]
  )

  /** Requests the language server to save a file on behalf of a given user.
    *
    * @param clientId the client closing the file.
    * @param path the file path.
    * @param currentVersion the current version evaluated on the client side.
    */
  case class SaveFile(
    clientId: ClientId,
    path: Path,
    currentVersion: TextApi.Version
  )

  /** Signals the result of saving a file. */
  sealed trait SaveFileResult

  /** Signals that saving a file was executed successfully. */
  case object FileSaved extends SaveFileResult

  /** Signals that the client doesn't hold write lock to the buffer. */
  case object SaveDenied extends SaveFileResult

  /** Signals that version provided by a client doesn't match to the version
    * computed by the server.
    *
    * @param clientVersion a version send by the client
    * @param serverVersion a version computed by the server
    */
  case class SaveFileInvalidVersion(
    clientVersion: TextApi.Version,
    serverVersion: TextApi.Version
  ) extends SaveFileResult

  /** Signals that saving a file failed due to IO error.
    *
    * @param fsFailure a filesystem failure
    */
  case class SaveFailed(fsFailure: FileSystemFailure) extends SaveFileResult

  /** Signals that the file is modified on disk. */
  case object SaveFailedFileModifiedOnDisk extends SaveFileResult

  /** Read the contents of a collaborative buffer.
    *
    * @param path the file path
    */
  case class ReadCollaborativeBuffer(path: Path)

  /** The data carried by a successful read operation.
    *
    * @param buffer file contents
    */
  case class ReadCollaborativeBufferResult(buffer: Option[Buffer])

  trait Codecs {

    private object IdMapOldCodecs {

      private object CodecField {
        val Index = "index"
        val Size  = "size"
        val Value = "value"
      }

      implicit private val spanDecoder: Decoder[Span] =
        Decoder.instance { cursor =>
          for {
            index <- cursor
              .downField(CodecField.Index)
              .downField(CodecField.Value)
              .as[Int]
            size <- cursor
              .downField(CodecField.Size)
              .downField(CodecField.Value)
              .as[Int]
          } yield Span(index, index + size)
        }

      implicit def idMapDecoder: Decoder[IdMap] =
        Decoder.instance { cursor =>
          for {
            pairs <- Decoder[Vector[(Span, UUID)]].tryDecode(cursor)
          } yield IdMap(pairs)
        }
    }

    private object IdMapCodecs {

      implicit def idMapEncoder: Encoder[IdMap] =
        Encoder.instance { idMap =>
          Json.fromValues(
            idMap.values
              .map({ case (span, uuid) =>
                Json.arr((span.start, span.length, uuid).asJson)
              })
          )
        }

      implicit def idMapDecoder: Decoder[IdMap] =
        Decoder.instance { cursor =>
          for {
            triples <- Decoder[Vector[(Int, Int, UUID)]].tryDecode(cursor)
          } yield {
            val pairs = triples.map({ case (start, length, uuid) =>
              Span(start, start + length) -> uuid
            })
            IdMap(pairs)
          }
        }
    }

    implicit val encoder: Encoder[IdMap] =
      IdMapCodecs.idMapEncoder

    implicit val decoder: Decoder[IdMap] =
      IdMapCodecs.idMapDecoder.or(IdMapOldCodecs.idMapDecoder)
  }
}
