package com.bytedance.idea.plugin.prism

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class PrismConfigurable : Configurable {

    private val enableCheckbox = JCheckBox()
    private val textField = JTextField()

    init {
        textField.columns = 30
        textField.maximumSize = Dimension(Int.MAX_VALUE, 30)
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
        textField.text = settings.frameworkJarPath

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
        panel.add(textField, c)

        // 将表单面板添加到外层 panel 的 NORTH，让其靠上
        outerPanel.add(panel, BorderLayout.NORTH)
        return outerPanel
    }

    override fun isModified(): Boolean {
        val settings = PrismSettings.getInstance()
        return enableCheckbox.isSelected != settings.enabled ||
                textField.text != settings.frameworkJarPath
    }

    override fun apply() {
        val path = textField.text
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