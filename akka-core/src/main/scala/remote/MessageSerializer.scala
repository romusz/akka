/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

package se.scalablesolutions.akka.remote

import se.scalablesolutions.akka.serialization.{Serializer, Serializable}
import se.scalablesolutions.akka.remote.protocol.RemoteProtocol._

import com.google.protobuf.{Message, ByteString}

object MessageSerializer {
  private var SERIALIZER_JAVA:       Serializer.Java      = Serializer.Java
  private var SERIALIZER_JAVA_JSON:  Serializer.JavaJSON  = Serializer.JavaJSON
  private var SERIALIZER_SCALA_JSON: Serializer.ScalaJSON = Serializer.ScalaJSON
  private var SERIALIZER_SBINARY:    Serializer.SBinary   = Serializer.SBinary
  private var SERIALIZER_PROTOBUF:   Serializer.Protobuf  = Serializer.Protobuf

  def setClassLoader(cl: ClassLoader) = {
    SERIALIZER_JAVA.classLoader       = Some(cl)
    SERIALIZER_JAVA_JSON.classLoader  = Some(cl)
    SERIALIZER_SCALA_JSON.classLoader = Some(cl)
    SERIALIZER_SBINARY.classLoader    = Some(cl)
  }

  def deserialize(messageProtocol: MessageProtocol): Any = {
    messageProtocol.getSerializationScheme match {
      case SerializationSchemeType.JAVA =>
        unbox(SERIALIZER_JAVA.fromBinary(messageProtocol.getMessage.toByteArray, None))
      case SerializationSchemeType.SBINARY =>
        val classToLoad = new String(messageProtocol.getMessageManifest.toByteArray)
        val clazz = if (SERIALIZER_SBINARY.classLoader.isDefined) SERIALIZER_SBINARY.classLoader.get.loadClass(classToLoad)
                    else Class.forName(classToLoad)
        val renderer = clazz.newInstance.asInstanceOf[Serializable.SBinary[_ <: AnyRef]]
        renderer.fromBytes(messageProtocol.getMessage.toByteArray)
      case SerializationSchemeType.SCALA_JSON =>
        val manifest = SERIALIZER_JAVA.fromBinary(messageProtocol.getMessageManifest.toByteArray, None).asInstanceOf[String]
        SERIALIZER_SCALA_JSON.fromBinary(messageProtocol.getMessage.toByteArray, Some(Class.forName(manifest)))
      case SerializationSchemeType.JAVA_JSON =>
        val manifest = SERIALIZER_JAVA.fromBinary(messageProtocol.getMessageManifest.toByteArray, None).asInstanceOf[String]
        SERIALIZER_JAVA_JSON.fromBinary(messageProtocol.getMessage.toByteArray, Some(Class.forName(manifest)))
      case SerializationSchemeType.PROTOBUF =>
        val messageClass = SERIALIZER_JAVA.fromBinary(messageProtocol.getMessageManifest.toByteArray, None).asInstanceOf[Class[_]]
        SERIALIZER_PROTOBUF.fromBinary(messageProtocol.getMessage.toByteArray, Some(messageClass))
    }
  }

  def serialize(message: Any): MessageProtocol = {
    val builder = MessageProtocol.newBuilder
    if (message.isInstanceOf[Serializable.SBinary[_]]) {
      val serializable = message.asInstanceOf[Serializable.SBinary[_ <: Any]]
      builder.setSerializationScheme(SerializationSchemeType.SBINARY)
      builder.setMessage(ByteString.copyFrom(serializable.toBytes))
      builder.setMessageManifest(ByteString.copyFrom(serializable.getClass.getName.getBytes))
    } else if (message.isInstanceOf[Message]) {
      val serializable = message.asInstanceOf[Message]
      builder.setSerializationScheme(SerializationSchemeType.PROTOBUF)
      builder.setMessage(ByteString.copyFrom(serializable.toByteArray))
      builder.setMessageManifest(ByteString.copyFrom(SERIALIZER_JAVA.toBinary(serializable.getClass)))
    } else if (message.isInstanceOf[Serializable.ScalaJSON]) {
      val serializable = message.asInstanceOf[Serializable.ScalaJSON]
      builder.setSerializationScheme(SerializationSchemeType.SCALA_JSON)
      builder.setMessage(ByteString.copyFrom(serializable.toBytes))
      builder.setMessageManifest(ByteString.copyFrom(serializable.getClass.getName.getBytes))
    } else if (message.isInstanceOf[Serializable.JavaJSON]) {
      val serializable = message.asInstanceOf[Serializable.JavaJSON]
      builder.setSerializationScheme(SerializationSchemeType.JAVA_JSON)
      builder.setMessage(ByteString.copyFrom(serializable.toBytes))
      builder.setMessageManifest(ByteString.copyFrom(serializable.getClass.getName.getBytes))
    } else {
      // default, e.g. if no protocol used explicitly then use Java serialization
      builder.setSerializationScheme(SerializationSchemeType.JAVA)
      builder.setMessage(ByteString.copyFrom(SERIALIZER_JAVA.toBinary(box(message))))
    }
    builder.build
  }

  private def box(value: Any): AnyRef = value match {
    case value: Boolean => new java.lang.Boolean(value)
    case value: Char => new java.lang.Character(value)
    case value: Short => new java.lang.Short(value)
    case value: Int => new java.lang.Integer(value)
    case value: Long => new java.lang.Long(value)
    case value: Float => new java.lang.Float(value)
    case value: Double => new java.lang.Double(value)
    case value: Byte => new java.lang.Byte(value)
    case value => value.asInstanceOf[AnyRef]
  }

  private def unbox(value: AnyRef): Any = value match {
    case value: java.lang.Boolean => value.booleanValue
    case value: java.lang.Character => value.charValue
    case value: java.lang.Short => value.shortValue
    case value: java.lang.Integer => value.intValue
    case value: java.lang.Long => value.longValue
    case value: java.lang.Float => value.floatValue
    case value: java.lang.Double => value.doubleValue
    case value: java.lang.Byte => value.byteValue
    case value => value
  }

}
