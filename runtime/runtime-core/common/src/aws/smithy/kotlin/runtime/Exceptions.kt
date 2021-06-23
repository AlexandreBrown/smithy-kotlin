/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Additional metadata about an error
 */
open class ErrorMetadata {
    @InternalApi
    val attributes: Attributes = Attributes()

    companion object {
        /**
         * Set if an error is retryable
         */
        val Retryable: AttributeKey<Boolean> = AttributeKey("Retryable")
    }

    val isRetryable: Boolean
        get() = attributes.getOrNull(Retryable) ?: false
}

/**
 * Base exception class for all exceptions thrown by the SDK. Exception may be a client side exception or a service exception
 */
open class SdkBaseException : RuntimeException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    /**
     * Additional metadata about the error
     */
    open val sdkErrorMetadata: ErrorMetadata = ErrorMetadata()
}

/**
 * Base exception class for any errors that occur while attempting to use an SDK client to make (Smithy) service calls.
 */
open class ClientException : SdkBaseException {
    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}

/**
 * Generic interface that any protocol (e.g. HTTP, MQTT, etc) can extend to provide additional access to
 * protocol specific details.
 */
interface ProtocolResponse

private object EmptyProtocolResponse : ProtocolResponse

open class ServiceErrorMetadata : ErrorMetadata() {

    companion object {
        val ErrorType: AttributeKey<ServiceException.ErrorType> = AttributeKey("ErrorType")
        val ProtocolResponse: AttributeKey<ProtocolResponse> = AttributeKey("ProtocolResponse")
    }

    /**
     * The name of the service that sent this error response
     */
    val serviceName: String
        get() = attributes.getOrNull(SdkClientOption.ServiceName) ?: ""

    /**
     * Indicates who is responsible for this exception (caller, service, or unknown)
     */
    val errorType: ServiceException.ErrorType
        get() = attributes.getOrNull(ErrorType) ?: ServiceException.ErrorType.Unknown

    /**
     * The protocol response if available (this will differ depending on the underlying protocol e.g. HTTP, MQTT, etc)
     */
    val protocolResponse: ProtocolResponse
        get() = attributes.getOrNull(ProtocolResponse) ?: EmptyProtocolResponse
}

/**
 * ServiceException - Base exception class for any error response returned by a service. Receiving an exception of this
 * type indicates that the caller's request was successfully transmitted to the service and the service sent back an
 * error response.
 */
open class ServiceException : SdkBaseException {

    /**
     * Indicates who (if known) is at fault for this exception.
     */
    enum class ErrorType {
        Client,
        Server,
        Unknown
    }

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    override val sdkErrorMetadata: ServiceErrorMetadata = ServiceErrorMetadata()
}