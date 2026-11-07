package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.StringRes
import com.mapcontrol.R

/**
 * "Tümü" sayfası: iki sekme, **Son aramalar** ve **Kişiler**, bellekteki tam rehber/geçmişten ([PhoneSource.directory]).
 * Satırlar geri dönüştürülen bir ListView'de (76dp; dokun: ara). Liste yalnızca [PhoneSnapshot.directoryVersion] değişince
 * yeniden yüklenir. Açılış kullanıcının açık eylemidir: [PhoneSource.refreshData] ([RefreshReason.USER_OPENED]) bağlantı
 * başına en çok bir kez telefondan indirme isteyebilir (karar arka uçta).
 *
 * GİZLİLİK: isim ve numaralar yalnızca ekrana yazılır; günlüğe ya da diske hiçbir şey gitmez. Fotoğraf yalnız [PhonePhotos].
 */
internal class PhoneAllSheet private constructor(private val activity: Activity) : PhoneListener {

    enum class Tab { RECENTS, CONTACTS }

    private val context: Context = activity
    private var tab = Tab.RECENTS
    private var shownVersion = -1
    private var dir = PhoneDirectory.EMPTY
    private var dialog: Dialog? = null
    private val tabViews = HashMap<Tab, TextView>()
    private lateinit var list: ListView
    private lateinit var empty: View
    private val adapter = RowAdapter()

    override fun onPhoneChanged(snapshot: PhoneSnapshot) {
        if (snapshot.directoryVersion == shownVersion) return
        reload()
    }

    override fun onPhoneError(error: PhoneError) {
        if (dialog?.isShowing != true) return
        PhoneToast.show(context, PhoneUiLogic.errorRes(error))
    }

    private fun reload() {
        dir = PhoneSource.directory()
        shownVersion = dir.version
        adapter.notifyDataSetChanged()
    }

    private fun build(container: LinearLayout, d: Dialog) {
        val tabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(context, Sahne.SOFT_FILL, Sahne.SQUARE_RADIUS_DP, Sahne.HAIR_2)
            val p = context.dp(4)
            setPadding(p, p, p, p)
            addView(tabView(Tab.RECENTS, R.string.phone_tab_recents), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(tabView(Tab.CONTACTS, R.string.phone_tab_contacts), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = context.dp(4)
            })
        }
        container.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(TOUCH_DP)))
        list = ListView(context).apply {
            divider = ColorDrawable(Sahne.HAIR)
            dividerHeight = 1
            selector = ColorDrawable(0)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = this@PhoneAllSheet.adapter
            setOnItemClickListener { _, _, pos, _ ->
                val number = when (tab) {
                    Tab.RECENTS -> dir.recents.getOrNull(pos)?.number
                    Tab.CONTACTS -> dir.contacts.getOrNull(pos)?.number
                } ?: return@setOnItemClickListener
                if (phoneDial(context, number)) d.dismiss()
            }
        }
        empty = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(sahneText(context, 22f, Sahne.INK_2, 500).apply { setText(R.string.phone_all_empty); gravity = Gravity.CENTER })
            addView(sahneText(context, 18f, Sahne.INK_3, 400).apply { setText(R.string.phone_all_empty_sub); gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) })
        }
        container.addView(FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(LIST_H_DP)).apply { topMargin = context.dp(12) })
        list.emptyView = empty
        selectTab(tab)
    }

    private fun tabView(t: Tab, @StringRes label: Int): TextView = sahneText(context, 20f, Sahne.INK_2, 600).apply {
        setText(label)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setOnClickListener { selectTab(t) }
        pressFeedback()
        tabViews[t] = this
    }

    private fun selectTab(t: Tab) {
        tab = t
        tabViews.forEach { (k, v) ->
            val on = k == t
            v.isSelected = on
            v.background = if (on) GlassDrawable(context, Sahne.SQUARE_RADIUS_DP - 4f, shadow = 0.3f, control = true) else null
            v.setTextColor(if (on) Sahne.INK else Sahne.INK_3)
        }
        adapter.notifyDataSetChanged()
        if (::list.isInitialized) list.setSelection(0)
    }

    /** Geri dönüştürülen satırlar: avatar + ad (yoksa numara) + alt satır (son aramada tür + zaman, kişide numara). */
    private inner class RowAdapter : BaseAdapter() {
        override fun getCount(): Int = if (tab == Tab.RECENTS) dir.recents.size else dir.contacts.size
        override fun getItem(position: Int): Any? = null
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h = (convertView?.tag as? Holder) ?: Holder()
            when (tab) {
                Tab.RECENTS -> dir.recents.getOrNull(position)?.let { r ->
                    val named = PhoneUiLogic.hasName(r.name)
                    val label = if (named) r.name!!.trim() else r.number
                    setTextIfChanged(h.title, label)
                    h.avatar.bind(r.photoKey, if (named) PhoneText.initials(r.name) else "#")
                    h.glyph.visibility = View.VISIBLE
                    h.glyph.setImageResource(recentGlyph(r.type))
                    // Cevapsız: işaret yanar (ALERT), ad sakin.
                    h.glyph.imageTintList = ColorStateList.valueOf(if (r.type == RecentType.MISSED) Sahne.ALERT else Sahne.INK_3)
                    val time = phoneTimeText(context, r.timeMs, ago = false)
                    setTextIfChanged(h.sub, if (named) listOf(time, r.number).filter { it.isNotEmpty() }.joinToString("  ·  ") else time)
                    h.root.contentDescription = context.getString(R.string.launcher_phone_call_cd, label) + ", " + time
                }
                Tab.CONTACTS -> dir.contacts.getOrNull(position)?.let { c ->
                    setTextIfChanged(h.title, c.name)
                    h.avatar.bind(c.photoKey, c.initials)
                    h.glyph.visibility = View.GONE
                    setTextIfChanged(h.sub, c.number)
                    h.root.contentDescription = context.getString(R.string.launcher_phone_call_cd, c.name)
                }
            }
            return h.root
        }
    }

    private inner class Holder {
        val avatar = PhoneAvatarView(context, AVATAR_DP, 19f)
        val title: TextView = sahneText(context, 20f, Sahne.INK, 500).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val glyph = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val sub: TextView = sahneText(context, 16f, Sahne.INK_3, 400).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val root: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(TOUCH_DP)
            setPadding(context.dp(8), 0, context.dp(8), 0)
            // Avatar gölgesi kırpılmasın (satır içinde kalır).
            clipChildren = false
            addView(avatar, LinearLayout.LayoutParams(context.dp(AVATAR_DP), context.dp(AVATAR_DP)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(glyph, LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(6) })
                    addView(sub, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(2) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(16) })
            layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(TOUCH_DP))
            tag = this@Holder
        }
    }

    companion object {
        private const val TOUCH_DP = 76
        private const val AVATAR_DP = 52
        private const val LIST_H_DP = 440
        private const val WIDTH_DP = 760

        /** Sayfayı açar; kapanınca dinleme biter. Dönen diyaloğu etkinlik izler (gelen aramada kapatılır). */
        fun show(activity: Activity, start: Tab = Tab.RECENTS, onDismiss: (() -> Unit)? = null): Dialog {
            val s = PhoneAllSheet(activity)
            s.tab = start
            val d = showLauncherSheet(activity, R.string.phone_all, WIDTH_DP) { dialog -> s.build(this, dialog) }
            s.dialog = d
            PhoneSource.start(activity, s)
            PhoneSource.addListener(s)
            s.reload()
            // Kullanıcının açık eylemi: önbellek, gerekirse bağlantı başına bir kez indirme (karar arka uçta).
            PhoneSource.refreshData(RefreshReason.USER_OPENED)
            d.setOnDismissListener {
                PhoneSource.removeListener(s)
                PhoneSource.stop(s)
                s.dialog = null
                onDismiss?.invoke()
            }
            return d
        }
    }
}
