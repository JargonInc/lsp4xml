/**
 *  Copyright (c) 2018 Angelo ZERR
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v20.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.lsp4xml.extensions.xsd.participants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.xerces.xni.XMLLocator;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4xml.dom.DOMDocument;
import org.eclipse.lsp4xml.dom.DOMNode;
import org.eclipse.lsp4xml.extensions.contentmodel.participants.codeactions.s4s_elt_invalid_content_3CodeAction;
import org.eclipse.lsp4xml.services.extensions.ICodeActionParticipant;
import org.eclipse.lsp4xml.services.extensions.diagnostics.IXMLErrorCode;
import org.eclipse.lsp4xml.utils.XMLPositionUtility;

/**
 * XSD error code.
 * 
 * @see https://wiki.xmldation.com/Support/Validator
 *
 */
public enum XSDErrorCode implements IXMLErrorCode {
	
	cos_all_limited_2("cos-all-limited.2"),
	ct_props_correct_3("ct-props-correct.3"),
	p_props_correct_2_1("p-props-correct.2.1"),
	s4s_elt_invalid_content_1("s4s-elt-invalid-content.1"), //
	s4s_elt_must_match_1("s4s-elt-must-match.1"), //
	s4s_elt_must_match_2("s4s-elt-must-match.2"),
	s4s_att_must_appear("s4s-att-must-appear"), //
	s4s_elt_invalid_content_2("s4s-elt-invalid-content.2"), //
	s4s_att_not_allowed("s4s-att-not-allowed"), //
	s4s_att_invalid_value("s4s-att-invalid-value"), //
	s4s_elt_character("s4s-elt-character"), //
	s4s_elt_invalid_content_3("s4s-elt-invalid-content.3"), //
	sch_props_correct_2("sch-props-correct.2"),
	src_ct_1("src-ct.1"),
	src_element_3("src-element.3"),
	src_resolve_4_2("src-resolve.4.2"), //
	src_resolve("src-resolve"), src_element_2_1("src-element.2.1"),
	EmptyTargetNamespace("EmptyTargetNamespace");

	private final String code;

	private XSDErrorCode() {
		this(null);
	}

	private XSDErrorCode(String code) {
		this.code = code;
	}

	@Override
	public String getCode() {
		if (code == null) {
			return name();
		}
		return code;
	}

	private final static Map<String, XSDErrorCode> codes;

	static {
		codes = new HashMap<>();
		for (XSDErrorCode errorCode : values()) {
			codes.put(errorCode.getCode(), errorCode);
		}
	}

	public static XSDErrorCode get(String name) {
		return codes.get(name);
	}

	/**
	 * Create the LSP range from the SAX error.
	 * 
	 * @param characterOffset
	 * @param key
	 * @param arguments
	 * @param document
	 * @return the LSP range from the SAX error.
	 */
	public static Range toLSPRange(XMLLocator location, XSDErrorCode code, Object[] arguments, DOMDocument document) {
		int offset = location.getCharacterOffset() - 1;
		// adjust positions
		switch (code) {
		case cos_all_limited_2: {
			String nameValue = (String) arguments[1];
			DOMNode parent = document.findNodeAt(offset);
			List<DOMNode> children = parent.getChildrenWithAttributeValue("name", nameValue);

			if (children.isEmpty()) {
				return XMLPositionUtility.selectStartTag(offset, document);
			}

			offset = children.get(0).getStart() + 1;
			return XMLPositionUtility.selectAttributeValueAt("maxOccurs", offset, document);
		}
		case ct_props_correct_3: {
			String attrValue = (String) arguments[0];
			if (attrValue.charAt(0) == ':') {
				attrValue = attrValue.substring(1);
			}
			return XMLPositionUtility.selectAttributeValueFromGivenValue(attrValue, offset, document);
		}
		case p_props_correct_2_1:
			return XMLPositionUtility.selectAttributeFromGivenNameAt("minOccurs", offset, document);
		case s4s_elt_invalid_content_1:
		case s4s_elt_must_match_1:
		case s4s_elt_must_match_2:
		case s4s_att_must_appear:
		case s4s_elt_invalid_content_2:
		case s4s_elt_invalid_content_3:
		case src_element_2_1:
		case src_element_3:
			return XMLPositionUtility.selectStartTag(offset, document);
		case s4s_att_not_allowed: {
			String attrName = (String) arguments[1];
			return XMLPositionUtility.selectAttributeNameFromGivenNameAt(attrName, offset, document);
		}
		case s4s_att_invalid_value: {
			String attrName = (String) arguments[1];
			return XMLPositionUtility.selectAttributeValueAt(attrName, offset, document);
		}
		case s4s_elt_character:
			return XMLPositionUtility.selectContent(offset, document);
		case sch_props_correct_2: {
			String argument = (String) arguments[0];
			String attrName = argument.substring(argument.indexOf(",") + 1);
			return XMLPositionUtility.selectAttributeValueFromGivenValue(attrName, offset, document);
		}
		case src_ct_1:
			return XMLPositionUtility.selectAttributeValueAt("base", offset, document);
		case src_resolve_4_2: {
			String attrValue = (String) arguments[2];
			return XMLPositionUtility.selectAttributeValueByGivenValueAt(attrValue, offset, document);
		}
		case src_resolve: {
			String attrValue = (String) arguments[0];
			return XMLPositionUtility.selectAttributeValueByGivenValueAt(attrValue, offset, document);
		}
		case EmptyTargetNamespace:
			return XMLPositionUtility.selectAttributeValueAt("targetNamespace", offset, document);
		}
		return null;
	}

	public static void registerCodeActionParticipants(Map<String, ICodeActionParticipant> codeActions) {
		codeActions.put(s4s_elt_invalid_content_3.getCode(), new s4s_elt_invalid_content_3CodeAction());
	}
}
