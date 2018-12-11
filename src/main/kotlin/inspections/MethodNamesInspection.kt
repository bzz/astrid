package inspections

import actions.SuggestionListPopupStep
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import downloader.Downloader
import inspections.SuggestionsStorage.Companion.recalculateLater
import model.ModelFacade
import utils.PsiUtils
import utils.PsiUtils.caretInsideMethodBlock
import utils.PsiUtils.hasSuperMethod
import java.nio.file.Files

class MethodNamesInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return MethodVisitor(holder)
    }

    class MethodVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        override fun visitMethod(method: PsiMethod?) {
            when {
                method == null -> return
                method.body == null -> return
                method.isConstructor -> return
                hasSuperMethod(method) -> return
                !Files.exists(Downloader.getModelPath()) -> return
                caretInsideMethodBlock(method) -> recalculateLater(method)
                else -> {
                    if (!SuggestionsStorage.contains(method) || SuggestionsStorage.needRecalculate(method)) {
                        val suggestionsList: List<String> = ModelFacade().getSuggestions(method)
                        SuggestionsStorage.put(method, suggestionsList)
                    }
                    val suggestions = SuggestionsStorage.getSuggestions(method)
                    if (suggestions.isNotEmpty() && !suggestions.contains(method.name)) {
                        holder.registerProblem(method.nameIdentifier ?: method, "Model has name suggestions for " +
                                "this method",
                                ProblemHighlightType.WEAK_WARNING,
                                RenameMethodQuickFix(suggestions))
                    }
                    super.visitMethod(method)
                }
            }
        }
    }

    class RenameMethodQuickFix(private val suggestions: List<String>) : LocalQuickFix {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement.containingFile
            val editor = FileEditorManager.getInstance(project).selectedTextEditor!!

            PsiUtils.executeWriteAction(project, file) {
                val listPopup = JBPopupFactory.getInstance().createListPopup(
                        SuggestionListPopupStep("Suggestions", suggestions, editor, file)
                )
                listPopup.showInBestPositionFor(editor)
            }
        }

        override fun getFamilyName(): String {
            return "Get method name suggestions"
        }

    }

    override fun getDisplayName(): String {
        return "Show method name suggestions"
    }

    override fun getGroupDisplayName(): String {
        return "Plugin Astrid"
    }

    override fun getShortName(): String {
        return "BestMethodName"
    }

}