package com.tyron.kotlin_completion.diagnostic

import com.tyron.builder.model.DiagnosticWrapper
import com.tyron.kotlin_completion.position.Position
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.openjdk.javax.tools.Diagnostic
import org.jetbrains.kotlin.diagnostics.Diagnostic as KotlinDiagnostic

fun convertDiagnostic(diagnostic: KotlinDiagnostic): List<DiagnosticWrapper> {
    val content = diagnostic.psiFile.text
    return diagnostic.textRanges.map {
        val range = Position.range(content, it)
        val wrapper = DiagnosticWrapper()
        wrapper.setMessage(message(diagnostic))
        wrapper.code = code(diagnostic)
        wrapper.kind = severity(diagnostic.severity)
        wrapper.source = diagnostic.psiFile.viewProvider.virtualFile
            .toNioPath().toFile()
        wrapper.startPosition = it.startOffset.toLong()
        wrapper.endPosition = it.endOffset.toLong()
        wrapper
    }
}

fun code(diagnostic: KotlinDiagnostic) =
    diagnostic.factory.name

fun message(diagnostic: KotlinDiagnostic) =
    DefaultErrorMessages.render(diagnostic)

fun severity(severity: Severity): Diagnostic.Kind =
    when(severity) {
        Severity.INFO -> Diagnostic.Kind.NOTE
        Severity.ERROR -> Diagnostic.Kind.ERROR
        Severity.WARNING -> Diagnostic.Kind.WARNING
    }