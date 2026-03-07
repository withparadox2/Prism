package com.bytedance.idea.plugin.prism

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

@Suppress("DialogTitleCapitalization")
class PrismConfigurable : Configurable {

    private val enableCheckbox = JCheckBox()
    private val pathField = TextFieldWithBrowseButton()

    init {
        pathField.textField.columns = 30
    }

    @Suppress("UseDPIAwareInsets")
    override fun createComponent(): JComponent {
        val outerPanel = JPanel(BorderLayout())
        outerPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.insets = Insets(4, 4, 4, 4)
        c.anchor = GridBagConstraints.WEST
        c.fill = GridBagConstraints.HORIZONTAL

        val settings = PrismSettings.getInstance()
        enableCheckbox.isSelected = settings.enabled
        pathField.text = settings.frameworkJarPath

        val descriptor = object : FileChooserDescriptor(
            true, false, false, true, false, false
        ) {
            override fun isFileSelectable(file: VirtualFile?): Boolean {
                return file?.extension?.lowercase() == "jar"
            }
        }

        pathField.addBrowseFolderListener(
            "Select framework.jar",
            "Choose framework.jar file",
            ProjectManager.getInstance().defaultProject,
            descriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )

        // 第一行：Enable 插件
        val label1 = JLabel("Enable plugin:")
        c.gridx = 0
        c.gridy = 0
        c.weightx = 0.0
        panel.add(label1, c)

        c.gridx = 1
        c.weightx = 1.0
        panel.add(enableCheckbox, c)

        // 第二行：framework.jar 路径
        val label2 = JLabel("framework.jar path:")
        c.gridx = 0
        c.gridy = 1
        c.weightx = 0.0
        panel.add(label2, c)

        c.gridx = 1
        c.weightx = 1.0
        panel.add(pathField, c)

        outerPanel.add(panel, BorderLayout.NORTH)
        return outerPanel
    }

    override fun isModified(): Boolean {
        val settings = PrismSettings.getInstance()
        return enableCheckbox.isSelected != settings.enabled || pathField.text != settings.frameworkJarPath
    }

    override fun apply() {
        val path = pathField.text

        if (enableCheckbox.isSelected && path.isNotBlank()) {
            val file = File(path)
            if (!file.exists()) {
                throw ConfigurationException("framework.jar 文件不存在，请检查路径")
            }
        }

        val settings = PrismSettings.getInstance()
        settings.enabled = enableCheckbox.isSelected
        settings.frameworkJarPath = path
    }

    override fun getDisplayName(): String = "Prism Settings"
}