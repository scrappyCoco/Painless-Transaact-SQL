/*
 * Copyright [2020] Coding4fun
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

package ru.coding4fun.tsql.action.folding

import com.intellij.database.dialects.mssql.model.MsDatabase
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbDataSource
import com.intellij.database.view.DatabaseStructure
import com.intellij.database.view.DatabaseView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.ui.tree.TreeUtil
import java.util.concurrent.CountDownLatch
import javax.swing.tree.TreePath

abstract class FoldingBaseAction(
        private val getPathToTop: (selectedPath: TreePath) -> TreePath?,
        private val getTopText: (parentObject: Any?) -> String,
        private val hideIfObjectNull: Boolean,
        private val isHideMode: Boolean,
        private val actionText: String
) : AnAction() {
    private lateinit var targetKind: ObjectKind
    private var topPath: TreePath? = null

    override fun actionPerformed(event: AnActionEvent) = object : Task.Modal(event.project, "Folding tree", true) {
        override fun run(indicator: ProgressIndicator) {
            val latch = CountDownLatch(1)
            val dbTree = event.getData(DatabaseView.DATABASE_VIEW_KEY)!!.tree
            val myVisitor = ObjectKindTreeVisitor(topPath, targetKind, { indicator.isCanceled }) { selectedPath ->
                isHideMode && dbTree.isCollapsed(selectedPath) && !dbTree.model.isLeaf(selectedPath.lastPathComponent)
            }
            TreeUtil.promiseVisit(dbTree, myVisitor).onSuccess {
                for (path in myVisitor.toHandleSet) {
                    if (path.pathCount == 1) continue
                    if (indicator.isCanceled) break
                    if (isHideMode) dbTree.collapsePath(path) else dbTree.expandPath(path)
                }
            }.then { latch.countDown() }
            latch.await()
        }
    }.queue()

    override fun update(event: AnActionEvent) {
        val dbTree = event.getData(DatabaseView.DATABASE_VIEW_KEY)?.tree
        if (dbTree == null) {
            event.presentation.isVisible = false
            return
        }
        val selectionPath = dbTree.selectionPath!!
        topPath = getPathToTop.invoke(selectionPath)
        val topComponent = topPath?.lastPathComponent
        val isItSelfSelected = selectionPath == topPath
        if (topPath == null && hideIfObjectNull || isItSelfSelected) {
            event.presentation.isVisible = false
        } else {
            targetKind = when (val selectedComponent = selectionPath.lastPathComponent) {
                is DatabaseStructure.FamilyGroup -> selectedComponent.getChildrenKind()
                is DasObject -> selectedComponent.kind
                else -> ObjectKind.DATABASE
            }
            val topText = getTopText(topComponent)
            val targetText = targetKind.name().toLowerCase()
            event.presentation.text = "$actionText all ${targetText}s $topText"
            event.presentation.isVisible = true
        }

    }

    companion object {
        private fun getTreePath(treePath: TreePath, lastIndex: Int): TreePath? {
            return if (lastIndex >= 0) TreePathUtil.convertCollectionToTreePath(treePath.path.take(lastIndex + 1)) else null
        }

        @Suppress("UNUSED_PARAMETER")
        fun getEverywhereObject(selectionPath: TreePath) = null

        fun getDataSourceObject(selectionPath: TreePath): TreePath? {
            val indexOfDbSource = selectionPath.path.indexOfFirst { it is DbDataSource }
            return getTreePath(selectionPath, indexOfDbSource)
        }

        fun getDbObject(selectionPath: TreePath): TreePath? {
            val indexOfDb = selectionPath.path.indexOfFirst { it is MsDatabase }
            return getTreePath(selectionPath, indexOfDb)
        }

        fun getGroupObject(selectionPath: TreePath): TreePath? {
            val indexOfDbGroup = selectionPath.path.indexOfLast { it.javaClass == DatabaseStructure.DbGroup::class.java }
            return getTreePath(selectionPath, indexOfDbGroup)
        }

        @Suppress("UNUSED_PARAMETER")
        fun getEverywhereText(parentObject: Any?) = "everywhere"

        fun getGroupText(parentObject: Any?): String {
            return "in " + (parentObject as DatabaseStructure.DbGroup).qualifiedName!!
        }

        fun getDasText(parentObject: Any?): String {
            return "in " + (parentObject as DasObject).name
        }
    }
}

abstract class ExpandBaseAction(
        getParentObject: ((treePath: TreePath) -> TreePath?),
        getParentText: ((parentObject: Any?) -> String),
        hideIfObjectNull: Boolean
) : FoldingBaseAction(getParentObject, getParentText, hideIfObjectNull, false, "Show")

abstract class CollapseBaseAction(
        getParentObject: ((treePath: TreePath) -> TreePath?),
        getParentText: ((parentObject: Any?) -> String),
        hideIfObjectNull: Boolean
) : FoldingBaseAction(getParentObject, getParentText, hideIfObjectNull, true, "Hide")

/*
    Using lambda instead of ::get
    https://youtrack.jetbrains.com/issue/KT-39389
 */

class ExpandEverywhereAction : ExpandBaseAction({ getEverywhereObject(it) }, { getEverywhereText(it) }, false)
class ExpandGroupAction : ExpandBaseAction({ getGroupObject(it) }, { getGroupText(it) }, true)
class ExpandDataSourceAction : ExpandBaseAction({ getDataSourceObject(it) }, { getDasText(it) }, true)
class ExpandDbAction : ExpandBaseAction({ getDbObject(it) }, { getDasText(it) }, true)

class CollapseEverywhereAction : CollapseBaseAction({ getEverywhereObject(it) }, { getEverywhereText(it) }, false)
class CollapseGroupAction : CollapseBaseAction({ getGroupObject(it) }, { getGroupText(it) }, true)
class CollapseDataSourceAction : CollapseBaseAction({ getDataSourceObject(it) }, { getDasText(it) }, true)
class CollapseDbAction : CollapseBaseAction({ getDbObject(it) }, { getDasText(it) }, true)