package ru.coding4fun.tsql.inspection.function.string

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.sql.dialects.SqlLanguageDialectEx
import com.intellij.sql.dialects.mssql.MsDialect
import com.intellij.sql.inspections.SqlInspectionBase
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlLiteralExpression
import com.intellij.sql.psi.impl.SqlPsiElementFactory
import ru.coding4fun.tsql.MsInspectionMessages

class MsSubstringInspection:  SqlInspectionBase(), CleanupLocalInspectionTool {
    override fun getGroupDisplayName(): String = MsInspectionMessages.message("inspection.function.string.group")
    override fun isDialectIgnored(dialect: SqlLanguageDialectEx?): Boolean = !(dialect?.dbms?.isMicrosoft ?: false)

    override fun createAnnotationVisitor(
            dialect: SqlLanguageDialectEx,
            manager: InspectionManager,
            problems: MutableList<ProblemDescriptor>,
            onTheFly: Boolean
    ): SqlAnnotationVisitor? {
        return SubstringVisitor(manager, dialect, problems, onTheFly)
    }

    private class SubstringVisitor(manager: InspectionManager,
                                      dialect: SqlLanguageDialectEx,
                                      problems: MutableList<ProblemDescriptor>,
                                      private val onTheFly: Boolean
    ) : SqlAnnotationVisitor(manager, dialect, problems) {
        override fun visitSqlFunctionCallExpression(funExpression: SqlFunctionCallExpression?) {
            if (!"SUBSTRING".equals(funExpression?.nameElement?.name, true)) {
                super.visitSqlFunctionCallExpression(funExpression)
                return
            }

            val parameters = funExpression?.parameterList?.expressionList
            if (parameters == null || parameters.size != 3) {
                super.visitSqlFunctionCallExpression(funExpression)
                return
            }

            val offsetParam = parameters[1] as? SqlLiteralExpression
            if (offsetParam == null) {
                super.visitSqlFunctionCallExpression(funExpression)
                return
            }

            if (offsetParam.text == "1") {
                val problemMessage = MsInspectionMessages.message("inspection.function.substring.problem")
                val funName = funExpression.nameElement!!
                val problem = myManager.createProblemDescriptor(
                        funName,
                        problemMessage,
                        true,
                        ProblemHighlightType.WEAK_WARNING,
                        onTheFly,
                        ReplaceSubstringToLeftQuickFix(funExpression)
                )
                addDescriptor(problem)
            }

            super.visitSqlFunctionCallExpression(funExpression)
        }
    }

    private class ReplaceSubstringToLeftQuickFix(substringCallExpression: SqlFunctionCallExpression) : LocalQuickFixOnPsiElement(substringCallExpression, substringCallExpression) {
        override fun getFamilyName(): String = MsInspectionMessages.message("inspection.function.substring.fix.family")
        override fun getText(): String = MsInspectionMessages.message("inspection.function.substring.fix.family")

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val funExpression = startElement as SqlFunctionCallExpression
            val parameters = funExpression.parameterList!!.expressionList
            val scriptBuilder = StringBuilder()
                    .append("LEFT(", parameters[0].text, ", ", parameters[2].text, ")")

            val leftFunExpression = SqlPsiElementFactory.createExpressionFromText(scriptBuilder.toString(), MsDialect.INSTANCE, project, null)!!
            funExpression.replace(leftFunExpression)
        }
    }
}