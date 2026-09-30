package io.legado.app.ui.book.import.remote

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.databinding.DialogWebdavServerBinding
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import org.json.JSONObject

class ServerConfigDialog() : BaseDialogFragment(R.layout.dialog_webdav_server, true),
    Toolbar.OnMenuItemClickListener {

    constructor(id: Long) : this() {
        arguments = Bundle().apply {
            putLong("id", id)
        }
    }

    private val binding by viewBinding(DialogWebdavServerBinding::bind)
    private val viewModel by viewModels<ServerConfigViewModel>()

    /**
     * WebDAV 与阅读服务器都是 url/username/password 三个字段,复用同一套输入框;
     * 差异只在 type 与序列化出的 config 类。
     */
    private val serverUi = listOf(
        RowUi("url"),
        RowUi("username"),
        RowUi("password", RowUi.Type.password)
    )

    private val spTypeReader = 1

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.inflateMenu(R.menu.server_config)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        viewModel.init(arguments?.getLong("id")) {
            upConfigView(viewModel.mServer)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> getServer().let {
                viewModel.save(it) {
                    dismissAllowingStateLoss()
                }
            }
        }
        return true
    }

    private fun upConfigView(server: Server?) {
        binding.etName.setText(server?.name)
        binding.spType.setSelection(
            when (server?.type) {
                Server.TYPE.READER -> spTypeReader
                else -> 0
            }
        )
        // upServerUi 只按键名读 url/username/password,两种类型的 config 都有这三个键
        upServerUi(server?.getConfigJsonObject())
    }

    private fun upServerUi(config: JSONObject?) {
        serverUi.forEachIndexed { index, rowUi ->
            when (rowUi.type) {
                RowUi.Type.text -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    binding.flexbox.addView(it.root)
                    it.root.id = index + 1000
                    it.textInputLayout.hint = rowUi.name
                    it.editText.setText(config?.getString(rowUi.name))
                }
                RowUi.Type.password -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    binding.flexbox.addView(it.root)
                    it.root.id = index + 1000
                    it.textInputLayout.hint = rowUi.name
                    it.editText.inputType =
                        InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                    it.editText.setText(config?.getString(rowUi.name))
                }
            }
        }
    }

    private fun getServer(): Server {
        val server = viewModel.mServer?.copy() ?: Server()
        server.name = binding.etName.text.toString()
        server.type = when (binding.spType.selectedItemPosition) {
            spTypeReader -> Server.TYPE.READER
            else -> Server.TYPE.WEBDAV
        }
        val values = serverUi.mapIndexed { index, _ ->
            val rowView = binding.root.findViewById<View>(index + 1000)
            ItemSourceEditBinding.bind(rowView).editText.text?.toString() ?: ""
        }
        val url = values.getOrElse(0) { "" }
        val username = values.getOrElse(1) { "" }
        val password = values.getOrElse(2) { "" }
        server.config = when (server.type) {
            Server.TYPE.READER -> {
                // accessToken/lastSyncAt 是运行时同步状态。地址或账号密码一变,已缓存的
                // token 与增量基准就失效了——那种情况下才清掉,否则每次改配置都要重新登录。
                val kept = viewModel.mServer?.getReaderServerConfig()
                    ?.takeIf { it.url == url && it.username == username && it.password == password }
                GSON.toJson(
                    Server.ReaderServerConfig(
                        url = url,
                        username = username,
                        password = password,
                        accessToken = kept?.accessToken ?: "",
                        lastSyncAt = kept?.lastSyncAt ?: 0L
                    )
                )
            }
            else -> GSON.toJson(Server.WebDavConfig(url, username, password))
        }
        return server
    }

}
