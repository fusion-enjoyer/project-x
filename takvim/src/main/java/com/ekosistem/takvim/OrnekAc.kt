package com.ekosistem.takvim

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract

/** Bir etkinlik örneğine dokununca açılacak ekran: etkinlik ayrıntısı, doğum gününde kişinin rehber kaydı. */
object OrnekAc {
    fun dogumGunuMu(o: Ornek) = o.etkinlikId < 0

    fun niyet(c: Context, o: Ornek): Intent =
        if (dogumGunuMu(o)) {
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, -o.etkinlikId))
        } else {
            Intent(c, DetayActivity::class.java)
                .putExtra(DetayActivity.EK_ID, o.etkinlikId).putExtra(DetayActivity.EK_BAS, o.baslangic)
                .putExtra(DetayActivity.EK_BIT, o.bitis)
        }
}
