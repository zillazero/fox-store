package com.looker.droidify.ui.repository

import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.looker.droidify.R
import com.looker.droidify.database.CursorOwner
import com.looker.droidify.databinding.ScreenRepositoriesBinding
import com.looker.droidify.service.Connection
import com.looker.droidify.service.SyncService
import com.looker.droidify.ui.ScreenFragment
import com.looker.droidify.utility.common.extension.dp
import com.looker.droidify.utility.common.extension.systemBarsMargin
import com.looker.droidify.utility.common.extension.systemBarsPadding
import com.looker.droidify.utility.extension.mainActivity
import com.looker.droidify.widget.addDivider
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode

class RepositoriesFragment : ScreenFragment(), CursorOwner.Callback {

    private var _binding: ScreenRepositoriesBinding? = null
    private val binding get() = _binding!!

    private val syncConnection = Connection(SyncService::class.java)

    private val scanQrCodeLauncher = registerForActivityResult(ScanQRCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val content = result.content.rawValue?.trim().orEmpty()
                if (content.isEmpty()) {
                    Snackbar
                        .make(
                            requireView(),
                            R.string.invalid_address,
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    return@registerForActivityResult
                }
                mainActivity.navigateAddRepository(repoAddress = content)
            }

            is QRResult.QRUserCanceled -> Unit
            is QRResult.QRMissingPermission -> {
                Snackbar.make(
                    requireView(),
                    R.string.qr_camera_permission_required,
                    Snackbar.LENGTH_LONG,
                ).show()
            }

            is QRResult.QRError -> {
                Snackbar.make(
                    requireView(),
                    R.string.qr_scan_failed,
                    Snackbar.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = ScreenRepositoriesBinding.inflate(inflater, container, false)
        val view = fragmentBinding.root.apply {
            binding.scanQr.visibility = View.GONE // FOX Store: keine fremden Quellen
            binding.addRepo.apply {
                // FOX Store: einziger Zusatz ist die geschützte Quelle „Privat“
                text = "Private Quelle"
                setOnClickListener { showPrivateDialog() }
                systemBarsMargin(16.dp)
            }
            binding.recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                isMotionEventSplittingEnabled = false
                setHasFixedSize(true)
                adapter = RepositoriesAdapter(
                    navigate = { mainActivity.navigateRepository(it.id) },
                ) { repository, isEnabled ->
                    repository.enabled != isEnabled &&
                        syncConnection.binder?.setEnabled(repository, isEnabled) == true
                }
                addDivider { _, _, configuration ->
                    configuration.set(
                        needDivider = true,
                        toTop = false,
                        paddingStart = 16.dp,
                        paddingEnd = 16.dp,
                    )
                }
                systemBarsPadding()
            }
            fragmentBinding.fragmentContent.addView(binding.root)
        }
        handleFab()
        return view
    }

    private fun privateRepository() = com.looker.droidify.database.Database.RepositoryAdapter.getAll()
        .firstOrNull { com.looker.droidify.filmverleih.PrivateRepo.isPrivate(it.address) }

    private fun showPrivateDialog() {
        val ctx = requireContext()
        val pr = com.looker.droidify.filmverleih.PrivateRepo
        val pad = (20 * resources.displayMetrics.density).toInt()
        val user = android.widget.EditText(ctx).apply { hint = "Benutzername"; setSingleLine() }
        val pass = android.widget.EditText(ctx).apply {
            hint = "Passwort"; setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0)
            addView(android.widget.TextView(ctx).apply {
                text = if (pr.hasCredentials()) "Angemeldet als ${pr.username()}. Neue Zugangsdaten überschreiben die alten." else
                    "Zugangsdaten für die geschützte Quelle eingeben. Sie werden verschlüsselt auf diesem Gerät gespeichert."
            })
            addView(user); addView(pass)
        }
        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Quelle „Privat“")
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                val u = user.text.toString().trim(); val p = pass.text.toString()
                if (u.isEmpty() || p.isEmpty()) return@setPositiveButton
                pr.save(u, p)
                privateRepository()?.let { syncConnection.binder?.setEnabled(it, true) }
                binding.recyclerView.adapter?.notifyDataSetChanged()
            }
            .setNegativeButton("Abbrechen", null)
        if (pr.hasCredentials()) {
            dlg.setNeutralButton(if (pr.isHidden()) "Einblenden" else "Ausblenden") { _, _ ->
                val hide = !pr.isHidden()
                pr.setHidden(hide)
                privateRepository()?.let { syncConnection.binder?.setEnabled(it, !hide) }
                binding.recyclerView.adapter?.notifyDataSetChanged()
            }
        }
        val shown = dlg.show()
        if (pr.hasCredentials()) {
            // Abmelden/entfernen über langen Druck auf "Abbrechen" wäre versteckt → eigener Button im Text
            box.addView(com.google.android.material.button.MaterialButton(ctx).apply {
                text = "Abmelden & Zugangsdaten entfernen"
                setOnClickListener {
                    privateRepository()?.let { syncConnection.binder?.setEnabled(it, false) }
                    pr.clear()
                    binding.recyclerView.adapter?.notifyDataSetChanged()
                    shown.dismiss()
                }
            })
        }
    }

    private fun handleFab() {
        binding.recyclerView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY) {
                binding.addRepo.shrink()
                binding.scanQr.hide()
            } else {
                binding.addRepo.extend()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        syncConnection.bind(requireContext())
        mainActivity.cursorOwner.attach(this, CursorOwner.Request.Repositories)
        mainActivity.onToolbarCreated(toolbar)
        toolbar.title = getString(R.string.repositories)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
        syncConnection.unbind(requireContext())
        mainActivity.cursorOwner.detach(this)
    }

    override fun onCursorData(request: CursorOwner.Request, cursor: Cursor?) {
        (binding.recyclerView.adapter as RepositoriesAdapter).cursor = cursor
    }
}
