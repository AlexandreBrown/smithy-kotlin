/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.util.push
import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

actual fun xmlStreamReader(payload: ByteArray): XmlStreamReader =
    XmlStreamReaderXmlPull(XmlStreamReaderXmlPull.xmlPullParserFactory(payload))

internal class XmlStreamReaderXmlPull(
    private val parser: XmlPullParser,
    private val minimumDepth: Int = parser.depth
) : XmlStreamReader {

    companion object {
        fun xmlPullParserFactory(payload: ByteArray, charset: Charset = Charsets.UTF_8): XmlPullParser {
            val factory = XmlPullParserFactory.newInstance("org.xmlpull.mxp1.MXParser", null)
            val parser = factory.newPullParser()
            parser.setFeature(MXParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(payload), charset.toString())
            return parser
        }
    }

    private val peekStack = mutableListOf(parser.takeNextValidToken())
    private var _lastToken = parser.lastToken()

    // In the case that a text node contains escaped characters,
    // the parser returns each of these as independent tokens however
    // they only represent a subset of the text value of a node.  To
    // deal with this we introduce a sub-token parsing mode in which
    // tokens are read and concatenated such that the client is only aware of
    // the complete Text values.
    private val subTokenStack = mutableListOf<String>()

    override val lastToken: XmlToken?
        get() = _lastToken

    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader {
        val currentReader = this
        val previousToken = lastToken
        val nextToken = internalPeek(1)

        if (nextToken.terminates(previousToken)) { // There is nothing in the subtree to return
            _lastToken = nextToken() // consume the subtree so parsing can continue
            return TerminalReader(currentReader)
        }

        val subTreeDepth = when (subtreeStartDepth) {
            XmlStreamReader.SubtreeStartDepth.CHILD -> _lastToken?.depth?.plus(1)
            XmlStreamReader.SubtreeStartDepth.CURRENT -> _lastToken?.depth
        } ?: throw DeserializationException("Unable to determine last node depth in $this")

        return SubTreeReader(this, subtreeStartDepth, subTreeDepth)
    }

    override fun nextToken(): XmlToken? {
        if (!hasNext()) return null

        return when (peekStack.isEmpty()) {
            true -> parser.takeNextValidToken()
            false -> peekStack.removeAt(0)
        }.also { token ->
            this._lastToken = token
        }
    }

    // This does one of three things:
    // 1: if the next token is BeginElement, then that node is skipped
    // 2: if the next token is Text or EndElement, read tokens until the end of the current node is exited
    // 3: if the next token is EndDocument, NOP
    override fun skipNext() {
        if (internalPeek(1).isTerminal()) return

        traverseNode(nextToken() ?: error("nextToken() unexpectedly returned null"), parser.depth)
    }

    override fun peek(index: Int): XmlToken? {
        val peekState = internalPeek(index)

        return if (peekState.isTerminal(minimumDepth)) null else peekState
    }

    private fun internalPeek(index: Int): XmlToken? {
        while (peekStack.size < index && parser.lastToken() != XmlToken.EndDocument) {
            peekStack.push(parser.takeNextValidToken())
        }

        return if (peekStack.size >= index) peekStack[index - 1] else null
    }

    private fun hasNext(): Boolean {
        val lastToken = parser.lastToken()
        val nextToken = internalPeek(1) ?: return false

        return lastToken.isNotTerminal() && nextToken.isNotTerminal(minimumDepth)
    }

    private tailrec fun traverseNode(st: XmlToken, startDepth: Int) {
        if (st == XmlToken.EndDocument) return
        if (st is XmlToken.EndElement && parser.depth == startDepth) return
        val next = nextToken() ?: return
        require(parser.depth >= startDepth) { "Traversal depth ${parser.depth} exceeded start node depth $startDepth" }
        return traverseNode(next, startDepth)
    }

    override fun toString(): String = "XmlStreamReader(last: $lastToken)"

    private fun XmlPullParser.takeNextValidToken(): XmlToken {
        try {
            do {
                this.next()
            } while (lastToken() == null)

            return lastToken()
                ?: throw DeserializationException(IllegalStateException("Unexpectedly unable to get next token"))
        } catch (e: XmlPullParserException) {
            throw DeserializationException(e)
        }
    }

    // Use the existence of sub tokens to determine if in general
    // parsing mode or in sub token parsing mode.
    private val isGeneralParseMode get() = subTokenStack.isEmpty()

    // Return the last valid token consumed as XmlToken, or null
    // if last token was not of a type we care about.
    private fun XmlPullParser.lastToken(): XmlToken? =
        when (this.eventType) {
            XmlPullParser.START_DOCUMENT -> null
            XmlPullParser.END_DOCUMENT -> XmlToken.EndDocument
            XmlPullParser.START_TAG -> XmlToken.BeginElement(
                depth,
                qualifiedName(),
                attributes(),
                currDeclaredNamespaces()
            )
            XmlPullParser.END_TAG -> when {
                isGeneralParseMode -> XmlToken.EndElement(depth, parser.qualifiedName())
                else -> {
                    val textValue = subTokenStack.joinToString(separator = "") { it }
                    val textToken = XmlToken.Text(depth, textValue)
                    subTokenStack.clear()
                    // Adding the synthetic text token to the peek stack and also returning it to the caller.
                    // This is because the returned value is discarded by internalPeek() and the token
                    // is read from peekStack.  However we must return a value to signal that tokens are
                    // available.
                    peekStack.add(0, textToken)
                    textToken
                }
            }
            XmlPullParser.CDSECT,
            XmlPullParser.DOCDECL,
            XmlPullParser.TEXT -> when {
                parser.text.isNullOrBlank() -> null
                isGeneralParseMode -> XmlToken.Text(depth, text)
                else -> {
                    subTokenStack.push(parser.text)
                    null
                }
            }
            XmlPullParser.ENTITY_REF -> {
                if (parser.text.isNotBlank()) subTokenStack.push(parser.text) // Add escaped character to sub token stack
                null
            }
            else -> null
        }
}

/**
 * Provides access to a subset of the XmlStream based on nodedepth.
 * @param currentReader parent reader.
 * @param subtreeStartDepth Take from current or child node depth.
 * @param minimumDepth minimum depth of the subtree
 */
private class SubTreeReader(
    private val currentReader: XmlStreamReader,
    private val subtreeStartDepth: XmlStreamReader.SubtreeStartDepth,
    private val minimumDepth: Int
) : XmlStreamReader {
    override val lastToken: XmlToken?
        get() = currentReader.lastToken

    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader =
        currentReader.subTreeReader(subtreeStartDepth)

    override fun nextToken(): XmlToken? {
        var peekToken = currentReader.peek(1) ?: return null

        if (subtreeStartDepth == XmlStreamReader.SubtreeStartDepth.CHILD && peekToken.depth < minimumDepth) {
            // Special case when a CHILD subtree is created on an end node, the next node will be a sibling
            // and fail the depth test.  In this case check the next node and if passed depth test skip to
            // it and return.
            peekToken = currentReader.peek(2) ?: return null
            if (peekToken.depth >= minimumDepth) currentReader.nextToken()
        }

        return if (peekToken.depth >= minimumDepth) currentReader.nextToken() else null
    }

    override fun skipNext() {
        currentReader.skipNext()
    }

    override fun peek(index: Int): XmlToken? {
        val peekToken = currentReader.peek(index) ?: return null

        return if (peekToken.depth >= minimumDepth) peekToken else null
    }

    override fun toString(): String = "$currentReader (subTree $minimumDepth)"
}

// A reader for a subtree with no children
private class TerminalReader(private val parent: XmlStreamReader) : XmlStreamReader {
    override val lastToken: XmlToken?
        get() = parent.lastToken

    override fun subTreeReader(subtreeStartDepth: XmlStreamReader.SubtreeStartDepth): XmlStreamReader = this

    override fun nextToken(): XmlToken? = null

    override fun skipNext() = Unit

    override fun peek(index: Int): XmlToken? = null
}

private fun XmlPullParser.qualifiedName(): XmlToken.QualifiedName =
    XmlToken.QualifiedName(name, prefix.blankToNull())

// Return attribute map from attributes of current node
private fun XmlPullParser.attributes(): Map<XmlToken.QualifiedName, String> =
    when (attributeCount) {
        0 -> emptyMap()
        else -> (0 until attributeCount)
            .asSequence()
            .map { attributeIndex ->
                XmlToken.QualifiedName(
                    getAttributeName(attributeIndex),
                    getAttributePrefix(attributeIndex).blankToNull()
                ) to getAttributeValue(attributeIndex)
            }
            .toMap()
    }

// get a list of all namespaces declared in this element
private fun XmlPullParser.currDeclaredNamespaces(): List<XmlToken.Namespace> {
    val nsStart = getNamespaceCount(depth - 1)
    val nsEnd = getNamespaceCount(depth)
    if (nsStart >= nsEnd) return emptyList()
    val decls = mutableListOf<XmlToken.Namespace>()
    for (i in nsStart until nsEnd) {
        val prefix = getNamespacePrefix(i)
        val ns = getNamespaceUri(i)
        decls.add(XmlToken.Namespace(ns, prefix))
    }
    return decls
}

private fun String?.blankToNull(): String? = if (this?.isBlank() == true) null else this

// Specific string to be able to eye-ball log output to determine node depth
private fun Int.indent(): String = ".  .".repeat(this - 1)
