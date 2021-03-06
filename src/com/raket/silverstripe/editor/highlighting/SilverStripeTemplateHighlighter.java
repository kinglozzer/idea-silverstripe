package com.raket.silverstripe.editor.highlighting;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import com.raket.silverstripe.SilverStripeLanguage;
import com.raket.silverstripe.psi.SilverStripeTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SilverStripeTemplateHighlighter extends LayeredLexerEditorHighlighter {
    public SilverStripeTemplateHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile, @NotNull EditorColorsScheme colors) {
        // create main highlighter
        super(new SilverStripeSyntaxHighlighter(), colors);

        // highlighter for outer lang
        FileType type = null;
        if(project == null || virtualFile == null) {
            type = StdFileTypes.PLAIN_TEXT;
        } else {
            Language language = TemplateDataLanguageMappings.getInstance(project).getMapping(virtualFile);
            if(language != null) type = language.getAssociatedFileType();
            if(type == null) type = SilverStripeLanguage.getDefaultTemplateLang();
        }
	    /*
        @SuppressWarnings ("deprecation") // deprecated in IDEA 12, still needed in IDEA 11 TODO remove when IDEA 11 support is dropped
                SyntaxHighlighter outerHighlighter = SyntaxHighlighter.PROVIDER.create(type, project, virtualFile);
        */
	    SyntaxHighlighter outerHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(type, project, virtualFile);
	    registerLayer(SilverStripeTypes.CONTENT, new LayerDescriptor(outerHighlighter, ""));
    }
}