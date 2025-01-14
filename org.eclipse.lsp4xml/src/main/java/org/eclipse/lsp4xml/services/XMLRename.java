/*******************************************************************************
* Copyright (c) 2019 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/

package org.eclipse.lsp4xml.services;

import static org.eclipse.lsp4xml.utils.XMLPositionUtility.covers;
import static org.eclipse.lsp4xml.utils.XMLPositionUtility.doesTagCoverPosition;
import static org.eclipse.lsp4xml.utils.XMLPositionUtility.getTagNameRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4xml.commons.BadLocationException;
import org.eclipse.lsp4xml.dom.DOMAttr;
import org.eclipse.lsp4xml.dom.DOMDocument;
import org.eclipse.lsp4xml.dom.DOMElement;
import org.eclipse.lsp4xml.dom.DOMNode;
import org.eclipse.lsp4xml.dom.parser.TokenType;
import org.eclipse.lsp4xml.services.extensions.XMLExtensionsRegistry;

/**
 * Handle all rename requests
 * 
 * Author:
 * Nikolas Komonen - nkomonen@redhat.com
 */
public class XMLRename {

	private static final Logger LOGGER = Logger.getLogger(XMLRename.class.getName());

	private final XMLExtensionsRegistry extensionsRegistry;

	public XMLRename(XMLExtensionsRegistry extensionsRegistry) {
		this.extensionsRegistry = extensionsRegistry;
	}

	public WorkspaceEdit doRename(DOMDocument xmlDocument, Position position, String newText) {
		List<TextEdit> textEdits = getRenameTextEdits(xmlDocument, position, newText);
		Map<String, List<TextEdit>> changes = new HashMap<>();
		changes.put(xmlDocument.getDocumentURI(), textEdits);
		return new WorkspaceEdit(changes);
	}

	public List<TextEdit> getRenameTextEdits(DOMDocument xmlDocument, Position position, String newText) {
		int offset = -1;
		try {
			offset = xmlDocument.offsetAt(position);
		} catch (BadLocationException e) {
			LOGGER.log(Level.SEVERE, "In XMLHighlighting the client provided Position is at a BadLocation", e);
			return Collections.emptyList();
		}
		DOMNode node = xmlDocument.findNodeAt(offset);
		if (node == null || !node.isElement() || ((DOMElement) node).getTagName() == null) {
			return Collections.emptyList();
		}
		Range startTagRange;
		Range endTagRange;
		if(node.isCDATA()) {
			Position startPos = null;
			Position endPos = null;
			Range tempRange = null;
			try {
				startPos = xmlDocument.positionAt(node.getStart());
				endPos = xmlDocument.positionAt(node.getEnd());
				tempRange = new Range(startPos, endPos);

			} catch (BadLocationException e) {
				LOGGER.log(Level.SEVERE, "In XMLRename the Node at provided Offset is a BadLocation", e);
				return Collections.emptyList();
			}
			if (covers(tempRange, position)) {
				startPos.setCharacter(startPos.getCharacter() + 1); // {Cursor}<![CDATA[ -> <{Cursor}![CDATA[
				endPos.setCharacter(endPos.getCharacter() - 1); // ]]>{Cursor} -> ]]{Cursor}>
				Position startPosEnd = new Position(startPos.getLine(), startPos.getCharacter() + 8);
				Position endPosStart = new Position(endPos.getLine(), endPos.getCharacter() - 2);
				startTagRange = new Range(startPos, startPosEnd);
				endTagRange = new Range(endPosStart, endPos);
				return getRenameList(startTagRange, endTagRange, newText);
			}
			return Collections.emptyList();
		}
		else if(node.isElement()) {
			DOMElement element = (DOMElement) node;
			startTagRange = getTagNameRange(TokenType.StartTag, node.getStart(), xmlDocument);
			endTagRange = element.hasEndTag() ? getTagNameRange(TokenType.EndTag, element.getEndTagOpenOffset(), xmlDocument)
					: null;
			if (doesTagCoverPosition(startTagRange, endTagRange, position)) {
				//Check if xsd namespace rename
				String fullNodeName = node.getNodeName();
				int indexOfColon = fullNodeName.indexOf(":");
				if(indexOfColon > 0) {
					Position startTagStartPosition = startTagRange.getStart();
					Position startTagPrefixPosition = new Position(startTagStartPosition.getLine(), startTagStartPosition.getCharacter() + indexOfColon);

					Position endTagStartPosition = endTagRange.getStart();
					Position endTagPrefixPosition = new Position(endTagStartPosition.getLine(), endTagStartPosition.getCharacter() + indexOfColon);

					Range startTagPrefixRange = new Range(startTagStartPosition, startTagPrefixPosition);
					Range endTagPrefixRange = new Range(endTagStartPosition, endTagPrefixPosition);

					if(doesTagCoverPosition(startTagPrefixRange, endTagPrefixRange, position)) {// Element prefix rename
						String prefix = element.getPrefix();
						return renameElementNamespace(xmlDocument, element, prefix.length(), newText);
					}
					else { //suffix rename without wiping namespace
						String suffixName = element.getLocalName();
						int suffixLength = suffixName.length();
						Position startTagEndPosition = startTagRange.getEnd();
						Position suffixStartPositionStart = new Position(startTagEndPosition.getLine(), startTagEndPosition.getCharacter() - suffixLength);

						Position endTagEndPosition = endTagRange.getEnd();
						Position suffixEndPositionStart = new Position(endTagEndPosition.getLine(), endTagEndPosition.getCharacter() - suffixLength);

						Range suffixRangeStart = new Range(suffixStartPositionStart, startTagEndPosition);
						Range suffixRangeEnd = new Range(suffixEndPositionStart, endTagEndPosition);

						return getRenameList(suffixRangeStart, suffixRangeEnd, newText);
					}
				}
				//Regular tag name rename
				return getRenameList(startTagRange, endTagRange, newText);
			}

			if(element.equals(xmlDocument.getDocumentElement())) { // If attribute xmlns:ATT_NAME was renamed
				List<DOMAttr> attributes = element.getAttributeNodes();

				if(attributes == null) {
					return Collections.emptyList();
				}

				for (DOMAttr attr : attributes) {
					DOMNode nameNode = attr.getNodeAttrName();

					if(!attr.isXmlns()) {
						continue;
					}

					Position start;
					Position end;
					try {
						start = xmlDocument.positionAt(nameNode.getStart() + "xmlns:".length());
						end = xmlDocument.positionAt(nameNode.getEnd());
					} catch (BadLocationException e) {
						continue;
					}
					
					if(covers(new Range(start, end), position)) { // Rename over the suffix of 'xmlns:XXX'
						String namespaceName = attr.getLocalName();
						return renameAllNamespaceOccurrences(xmlDocument, namespaceName, newText, attr);
					}
				}
			}
		}

		return Collections.emptyList();
	}

	/**
	 * Creates a list of start and end tag rename's.
	 * @param startTagRange
	 * @param endTagRange
	 * @param newText
	 * @return
	 */
	private static List<TextEdit> getRenameList(Range startTagRange, Range endTagRange, String newText) {
		List<TextEdit> result = new ArrayList<>(2);
				if (startTagRange != null) {
					result.add(new TextEdit(startTagRange, newText));
				}
				if (endTagRange != null) {
					result.add(new TextEdit(endTagRange, newText));
				}
				return result;
	}

	/**
	 * Renames all occurences of the namespace in a document, that match
	 * the given old namespace.
	 * @param document
	 * @param oldNamespace
	 * @param newNamespace
	 * @param rootAttr
	 * @return
	 */
	private static List<TextEdit> renameAllNamespaceOccurrences(DOMDocument document, String oldNamespace, String newNamespace, @Nullable DOMAttr rootAttr) {
		DOMElement rootElement = document.getDocumentElement();
		
		List<TextEdit> edits = new ArrayList<TextEdit>();

		// Renames the xmlns:NAME_SPACE attribute
		if(rootAttr != null) {
			Position start;
			try {
				start = document.positionAt(rootAttr.getStart() + "xmlns:".length());
			} catch (BadLocationException e) {
				start = null;
			}
		
			if(start != null) {
				Position end = new Position(start.getLine(), start.getCharacter() + oldNamespace.length());
				edits.add(new TextEdit(new Range(start, end), newNamespace));
			}
		}

		//Renames all elements with oldNamespace
		List<DOMNode> children = Arrays.asList(rootElement);
		return renameElementsNamespace(document, edits, children, oldNamespace, newNamespace);
	}

	/**
	 * Will traverse through the given elements and their children,
	 * updating all namespaces that match the given old namespace.
	 * @param document
	 * @param edits
	 * @param elements
	 * @param oldNamespace
	 * @param newNamespace
	 * @return
	 */
	private static List<TextEdit> renameElementsNamespace(DOMDocument document, List<TextEdit> edits, List<DOMNode> elements, String oldNamespace, String newNamespace) {
		int oldNamespaceLength = oldNamespace.length();
		for (DOMNode node : elements) {
			if(node.isElement()) {
				DOMElement element = (DOMElement) node;
				if(oldNamespace.equals(element.getPrefix())) {
					edits.addAll(renameElementNamespace(document, element, oldNamespaceLength, newNamespace));
				}
				if(element.hasAttributes()) {
					edits.addAll(renameElementAttributeValueNamespace(document, element, oldNamespace, newNamespace));
				}
				
				if(element.hasChildNodes()) {
					renameElementsNamespace(document, edits, element.getChildren(), oldNamespace, newNamespace);
				}
			}
		}
		
		return edits;
		
	}

	/**
	 * Will rename the namespace of a given element 
	 */
	private static List<TextEdit> renameElementNamespace(DOMDocument document, DOMElement element, int oldNamespaceLength, String newNamespace) {
		List<TextEdit> edits = new ArrayList<TextEdit>();
		Range[] ranges = createNamespaceRange(document, element, oldNamespaceLength);
		if(ranges == null) {
			return edits;
		}
		for (Range r : ranges) {
			if(r != null) {
				edits.add(new TextEdit(r, newNamespace));
			}
		}
		return edits;
	}

	/**
	 * Will rename the namespace of an element's attribute values with the matching namespace.
	 * @param document
	 * @param element
	 * @param oldNamespace
	 * @param newNamespace
	 * @return
	 */
	private static List<TextEdit> renameElementAttributeValueNamespace(DOMDocument document, DOMElement element, String oldNamespace, String newNamespace) {
		
		List<DOMAttr> attributes = element.getAttributeNodes();
		List<TextEdit> edits = new ArrayList<TextEdit>();
		if(attributes != null) {
			for (DOMAttr attr : attributes) {
				DOMNode attrValue = attr.getNodeAttrValue();
				if(attrValue != null) {
					String attrValueText = attr.getValue();
					if(attrValueText != null && attrValueText.startsWith(oldNamespace + ":")) {
						int startOffset = attrValue.getStart() + 1;

						Position start,end;
						try {
							start = document.positionAt(startOffset);
							end = new Position(start.getLine(), start.getCharacter() + oldNamespace.length());
						} catch (BadLocationException e) {
							return edits;
						}
						edits.add(new TextEdit(new Range(start, end), newNamespace));
					}
				}
			}
		}
		return edits;
	}

	/**
	 * Returns the ranges of the namespace of a start and end tag of an element.
	 * @param document
	 * @param element
	 * @param namespaceLength
	 * @return
	 */
	private static Range[] createNamespaceRange(DOMDocument document, DOMElement element, int namespaceLength) {
		Range[] ranges = new Range[2];

		Position start;
		Position end;
		try {
			if(element.hasStartTag()) {
				int startName = element.getStart() + 1; //skip '<'
				start = document.positionAt(startName);
				end = new Position(start.getLine(), start.getCharacter() + namespaceLength);
				ranges[0] = new Range(start, end);
			}
			if(element.hasEndTag()) {
				int startName = element.getEndTagOpenOffset() + 2; //skip '</'
				start = document.positionAt(startName);
				end = new Position(start.getLine(), start.getCharacter() + namespaceLength);
				ranges[1] = new Range(start, end);
			}
			return ranges;
		} catch (BadLocationException e) {
			return null;
		}
	}
}