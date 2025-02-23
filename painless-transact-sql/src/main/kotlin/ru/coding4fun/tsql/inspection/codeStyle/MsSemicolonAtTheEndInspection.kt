/*
 * Copyright [2019] Coding4fun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.coding4fun.tsql.inspection.codeStyle

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.sql.dialects.SqlLanguageDialectEx
import com.intellij.sql.dialects.mssql.MsDialect
import com.intellij.sql.inspections.SqlInspectionBase
import com.intellij.sql.psi.*
import com.intellij.sql.psi.impl.SqlPsiElementFactory
import ru.coding4fun.tsql.MsInspectionMessages
import ru.coding4fun.tsql.psi.getNextNotEmptyLeaf
import javax.swing.JComponent

class MsSemicolonAtTheEndInspection : SqlInspectionBase(), CleanupLocalInspectionTool {
    @Suppress("MemberVisibilityCanBePrivate")
    var preferSemicolonAtTheEnd = true

    override fun createOptionsPanel(): JComponent {
        val panel = MultipleCheckboxOptionsPanel(this)
        panel.addCheckbox(MsInspectionMessages.message("inspection.code.style.semicolon.at.the.end.option"), "preferSemicolonAtTheEnd")
        return panel
    }

    override fun getGroupDisplayName(): String = MsInspectionMessages.message("inspection.code.style.group")
    override fun isDialectIgnored(dialect: SqlLanguageDialectEx?): Boolean = !(dialect?.dbms?.isMicrosoft ?: false)

    override fun createAnnotationVisitor(
            dialectEx: SqlLanguageDialectEx,
            inspectionManager: InspectionManager,
            problems: MutableList<ProblemDescriptor>,
            onTheFly: Boolean
    ): SqlAnnotationVisitor {
        return SemicolonAtTheEndVisitor(inspectionManager, dialectEx, problems, onTheFly, preferSemicolonAtTheEnd)
    }

    private class SemicolonAtTheEndVisitor(manager: InspectionManager,
                                           dialect: SqlLanguageDialectEx,
                                           problems: MutableList<ProblemDescriptor>,
                                           private val onTheFly: Boolean,
                                           private val preferSemicolonAtTheEnd: Boolean
    ) : SqlAnnotationVisitor(manager, dialect, problems) {
        override fun visitSqlStatement(sqlStatement: SqlStatement?) {
            if (sqlStatement == null) return

            // 1. Skip for If statement (body will be inspected)
            // IF if_body ELSE else_body END

            // 2. For MERGE statement semicolon is required at the end of the statement/
            //@formatter:off

            // 3. Skip for BEGIN body END

            // 4. Skip for CREATE PROCEDURE AS body
            val isIgnorableStmt: Boolean = sqlStatement is SqlMergeStatement
                                        || sqlStatement is SqlIfStatement
                                        || sqlStatement is SqlBlockStatement
                                        || sqlStatement.children.any { it is SqlStatement }
            //@formatter:on
            if (isIgnorableStmt) {
                super.visitSqlStatement(sqlStatement)
                return
            }

            val lastLeaf = PsiTreeUtil.getDeepestVisibleLast(sqlStatement)
            if (lastLeaf == null) {
                super.visitSqlStatement(sqlStatement)
                return
            }

            val semicolonLeaf = lastLeaf.getNextNotEmptyLeaf()
            val isEndedWithSemicolon = semicolonLeaf?.elementType == SqlElementTypes.SQL_SEMICOLON
            if (isEndedWithSemicolon.xor(preferSemicolonAtTheEnd)) {
                val isNextIsWithCte = semicolonLeaf?.getNextNotEmptyLeaf()?.elementType == SqlElementTypes.SQL_WITH
                if (isNextIsWithCte && !preferSemicolonAtTheEnd) return

                val problemMessage = if (preferSemicolonAtTheEnd)
                    MsInspectionMessages.message("inspection.code.style.semicolon.at.the.end.fix.add")
                else MsInspectionMessages.message("inspection.code.style.semicolon.at.the.end.fix.remove")

                val highlightType = if (preferSemicolonAtTheEnd)
                    ProblemHighlightType.INFORMATION else ProblemHighlightType.LIKE_UNUSED_SYMBOL

                val problemElement = if (preferSemicolonAtTheEnd)
                    PsiTreeUtil.firstChild(sqlStatement) else semicolonLeaf!!

                val problem = myManager.createProblemDescriptor(
                        problemElement,
                        problemMessage,
                        true,
                        highlightType,
                        onTheFly,
                        SemicolonAtTheEndQuickFix(problemElement, preferSemicolonAtTheEnd)
                )
                addDescriptor(problem)
            } else {
                super.visitSqlStatement(sqlStatement)
            }
        }
    }

    private class SemicolonAtTheEndQuickFix(
            problemElement: PsiElement,
            private val preferSemicolonAtTheEnd: Boolean
    ) : LocalQuickFixOnPsiElement(problemElement) {
        override fun getFamilyName(): String = if (preferSemicolonAtTheEnd)
            MsInspectionMessages.message("inspection.code.style.semicolon.at.the.end.fix.add")
        else MsInspectionMessages.message("inspection.code.style.semicolon.at.the.end.fix.remove")

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            if (preferSemicolonAtTheEnd) {
                val semicolonLeaf = SqlPsiElementFactory.createLeafFromText(project, MsDialect.INSTANCE, ";")
                val statement = PsiTreeUtil.getParentOfType(startElement, SqlStatement::class.java)!!
                statement.parent.addAfter(semicolonLeaf, statement)
            } else {
                startElement.delete() // semicolonLeaf
            }
        }
    }
}