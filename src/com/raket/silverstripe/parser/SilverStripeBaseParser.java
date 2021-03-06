package com.raket.silverstripe.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import static com.raket.silverstripe.SilverStripeBundle.message;
import static com.raket.silverstripe.parser.SilverStripeParserStatements.*;
import static com.raket.silverstripe.psi.SilverStripeTypes.*;

/**
 * Created with IntelliJ IDEA.
 * User: Marcus Dalgren
 * Date: 2013-03-21
 * Time: 21:40
 */

public class SilverStripeBaseParser implements PsiParser {
	private IElementType nextToken;
	private String nextTokenText;
	private final Stack<Pair<PsiBuilder.Marker, String>> blockStack = new Stack<Pair<PsiBuilder.Marker, String>>();
	private final Stack<PsiBuilder.Marker> statementsStack = new Stack<PsiBuilder.Marker>();
	private PsiBuilder.Marker varMarker;
	private PsiBuilder.Marker blockMarker;
	private PsiBuilder.Marker commentMarker;
	private boolean markingVar;
	private boolean markingBlock;
	private boolean markingComment;
	private String blockTokenText;
	private IElementType blockType;
	private TokenSet blockStartTokens;
	private PsiBuilder.Marker identifierMarker;
	private boolean markingTheme;
	private PsiBuilder.Marker themeMarker = null;

	private void getNextTokenValue(PsiBuilder builder) {
		PsiBuilder.Marker rb = builder.mark();
		builder.advanceLexer();
		nextToken = builder.getTokenType();
		nextTokenText = builder.getTokenText();
		rb.rollbackTo();
	}

	IElementType getNextToken(PsiBuilder builder) {
		if (nextToken == null) {
			getNextTokenValue(builder);
		}
		return  nextToken;
	}

	String getNextTokenText(PsiBuilder builder) {
		if (nextTokenText == null) {
			getNextTokenValue(builder);
		}
		return  nextTokenText;
	}

	/**
	 * @param root    The file currently being parsed
	 * @param builder used to build the parse tree
	 * @return parsed tree
	 */

	@NotNull
	@Override
	public ASTNode parse(IElementType root, PsiBuilder builder) {
		PsiBuilder.Marker rootMarker = builder.mark();
		PsiBuilder.Marker wrapperMarker = builder.mark();
		builder.setDebugMode(true);
		// Process all tokens
		parseTree(builder);
		wrapperMarker.done(SS_STATEMENTS);
		rootMarker.done(root);
		return builder.getTreeBuilt();
	}

	// Tries to parse the whole tree
	private void parseTree(PsiBuilder builder) {
		IElementType type;

		while (!builder.eof()) {
			type = builder.getTokenType();
			beforeConsume(builder, type);
			builder.advanceLexer();
			afterConsume(builder, type);
			nextToken = null;
			nextTokenText = null;
		}
		eofCleanup();
	}

	private void beforeConsume(PsiBuilder builder, IElementType type) {
		if (type.equals(SS_VAR)) {
			varMarker = builder.mark();
			markingVar = true;
		}
		if (type.equals(SS_COMMENT_START)) {
			commentMarker = builder.mark();
			markingComment = true;
		}
		if (type.equals(SS_THEME_VAR)) {
			themeMarker = builder.mark();
			markingTheme = true;
		}
		
		if (type.equals(SS_BLOCK_START) && !markingBlock) {
			nextToken = getNextToken(builder);
			blockStartTokens = STATEMENT_MAP.get(nextToken);
			blockType = BLOCK_TYPE_MAP.get(nextToken);
			if (blockStartTokens != null) {
				blockTokenText = getNextTokenText(builder);
				blockMarker = builder.mark();
				markingBlock = true;
			}
		}

		if (markingTheme && !type.equals(SS_THEME_VAR) && !type.equals(SS_STRING)) {
			markingTheme = false;
		}
	}

	private void afterConsume(PsiBuilder builder, IElementType type) {
		if (type.equals(SS_VAR) && markingVar) {
			varMarker.done(NAMED_VAR);
			markingVar = false;
			identifierMarker = null;
		}

		if (markingTheme && type.equals(SS_THEME_VAR)) {
			themeMarker.done(SS_THEME_DIR);
		}

		if (type.equals(SS_STRING) && markingTheme) {
			PsiBuilder.Marker fullTheme = themeMarker.precede();
			fullTheme.done(SS_THEME_FILE_PATH);
			markingTheme = false;
			themeMarker = null;
		}

		if (type.equals(SS_IDENTIFIER)) {
			if (identifierMarker == null)
				identifierMarker = varMarker.precede();
			else
				identifierMarker = identifierMarker.precede();
			identifierMarker.done(SS_FIELD_REFERENCE);
		}

		if (type.equals(SS_COMMENT_END)) {
			commentMarker.done(SS_COMMENT_STATEMENT);
			markingComment = false;
		}

		if (markingBlock && type.equals(SS_BLOCK_END)) {
			blockMarker.done(blockType);
			markingBlock = false;
			if (BLOCK_STATEMENTS.contains(blockType)) {
				blockStack.push(Pair.create(blockMarker, blockTokenText));
				statementsStack.push(builder.mark());
			}
			else if (blockType.equals(SS_ELSE_IF_STATEMENT) || blockType.equals(SS_ELSE_STATEMENT)) {
				if (!statementsStack.isEmpty()) {
					statementsStack.pop().doneBefore(SS_STATEMENTS, blockMarker);
					statementsStack.push(builder.mark());
				}
			}
			else if (blockType.equals(SS_BLOCK_END_STATEMENT)) {
				PsiBuilder.Marker statementMarker = null;
				if (!statementsStack.isEmpty()) {
					statementMarker = statementsStack.pop();
					statementMarker.doneBefore(SS_STATEMENTS, blockMarker);
				}
				if (!blockStack.isEmpty()) {
					Pair<PsiBuilder.Marker, String> blockLevel = blockStack.peek();
					String endString = "end_"+blockLevel.getSecond();
					if (endString.equals(blockTokenText)) {
						PsiBuilder.Marker blockMarker = blockLevel.getFirst().precede();
						blockMarker.done(SS_BLOCK_STATEMENT);
						blockStack.pop();
					}
					else {
						blockStack.pop();
						if (statementMarker != null)
							statementMarker.drop();
					}
				}
			}

		}
		if (markingBlock && !blockStartTokens.contains(type)) {
			blockMarker.drop();
			markingBlock = false;
		}
	}

	private void eofCleanup() {
		if (markingBlock) {
			blockMarker.error(message("ss.parsing.unclosed.statement", blockTokenText));
			markingBlock = false;
		}
		if (markingComment) {
			commentMarker.drop();
		}
		while (!statementsStack.isEmpty()) {
			statementsStack.pop().drop();
		}
	}
}