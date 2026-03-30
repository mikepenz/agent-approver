package com.mikepenz.agentapprover.platform

import co.touchlab.kermit.Logger
import com.sun.jna.Callback
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO

/**
 * Uses JNA to overlay a colored badge as a native NSImageView subview on the
 * macOS NSStatusBarButton. This allows the main tray icon to be a template image
 * (auto-adapting to menu bar background) while the badge retains its custom color.
 *
 * Falls back silently on non-macOS or if native access fails.
 */
object MacOsTrayBadge {

    private const val BADGE_TAG = 0x4147_4E54L // "AGNT" — tag for our badge subview

    private val isMacOs = System.getProperty("os.name", "").contains("Mac", ignoreCase = true)

    // --- ObjC runtime helpers via JNA ---

    private val objcLib by lazy { NativeLibrary.getInstance("objc") }
    private val msgSendFn by lazy { objcLib.getFunction("objc_msgSend") }
    private val selRegFn by lazy { objcLib.getFunction("sel_registerName") }
    private val getClassFn by lazy { objcLib.getFunction("objc_getClass") }

    private fun cls(name: String): Pointer? =
        getClassFn.invoke(Pointer::class.java, arrayOf(name)) as? Pointer

    private fun sel(name: String): Pointer =
        selRegFn.invoke(Pointer::class.java, arrayOf(name)) as Pointer

    /** objc_msgSend returning a pointer (id), or null if nil. */
    private fun send(obj: Pointer, sel: Pointer, vararg args: Any?): Pointer? =
        msgSendFn.invoke(Pointer::class.java, arrayOf(obj, sel, *args)) as? Pointer

    /** objc_msgSend returning void. */
    private fun sendVoid(obj: Pointer, sel: Pointer, vararg args: Any?) {
        msgSendFn.invoke(Void.TYPE, arrayOf(obj, sel, *args))
    }

    /** objc_msgSend returning a long/NSInteger. */
    private fun sendLong(obj: Pointer, sel: Pointer, vararg args: Any?): Long =
        Pointer.nativeValue(msgSendFn.invoke(Pointer::class.java, arrayOf(obj, sel, *args)) as? Pointer)

    // --- GCD main thread dispatch via JNA ---

    /**
     * Callback interface for dispatch_async_f's work function.
     * void (*work)(void *context)
     */
    private interface DispatchFunction : Callback {
        fun invoke(context: Pointer?)
    }

    private val libSystem by lazy { NativeLibrary.getInstance("System") }
    private val dispatchAsyncF by lazy { libSystem.getFunction("dispatch_async_f") }

    /** The GCD main dispatch queue (_dispatch_main_q global symbol). */
    private val mainQueue: Pointer by lazy {
        libSystem.getGlobalVariableAddress("_dispatch_main_q")
    }

    /** Returns true if the current thread is the macOS main thread. */
    private fun isMainThread(): Boolean {
        return try {
            val nsThread = cls("NSThread") ?: return false
            val result = send(nsThread, sel("isMainThread"))
            Pointer.nativeValue(result) != 0L
        } catch (_: Exception) {
            false
        }
    }

    /** Dispatches a block to the macOS main thread via GCD and waits for completion. */
    private fun runOnMainThread(block: () -> Unit) {
        if (isMainThread()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        var thrown: Throwable? = null
        val callback = object : DispatchFunction {
            override fun invoke(context: Pointer?) {
                try {
                    block()
                } catch (e: Throwable) {
                    thrown = e
                } finally {
                    latch.countDown()
                }
            }
        }
        // dispatch_async_f(mainQueue, NULL, callback)
        dispatchAsyncF.invoke(Void.TYPE, arrayOf(mainQueue, null, callback))
        latch.await()
        thrown?.let { throw it }
    }

    // --- JNA struct for NSSize (used by setSize:) ---

    @Suppress("unused")
    @Structure.FieldOrder("width", "height")
    open class NSSize(
        @JvmField var width: Double = 0.0,
        @JvmField var height: Double = 0.0,
    ) : Structure(), Structure.ByValue

    // --- Public API ---

    /**
     * Adds or updates a colored badge overlay on the tray icon's native NSStatusBarButton.
     * The main tray icon image should be a template image (logo only, no badge).
     * This method adds the colored badge as a separate NSImageView subview.
     *
     * No-op on non-macOS or if native access fails.
     */
    fun update(trayIcon: java.awt.TrayIcon, pendingCount: Int) {
        if (!isMacOs) return

        // Prepare the badge image on the calling thread (no AppKit needed)
        val badgeBuffered = AppIcon.createTrayBadgeImage(pendingCount)
        val pngBytes = ByteArrayOutputStream().also { ImageIO.write(badgeBuffered, "PNG", it) }.toByteArray()

        // All AppKit/NSView operations must happen on the macOS main thread
        try {
            runOnMainThread {
                val targetView = getStatusBarButton(trayIcon) ?: return@runOnMainThread

                // Remove existing badge subview
                removeTaggedSubviews(targetView)

                // Create NSImage from PNG data
                val nsImage = createNSImage(pngBytes, pointWidth = 22.0, pointHeight = 22.0)
                    ?: return@runOnMainThread

                // Create NSImageView via alloc/init
                val nsImageViewClass = cls("NSImageView") ?: return@runOnMainThread
                val imageView = send(
                    send(nsImageViewClass, sel("alloc")) ?: return@runOnMainThread,
                    sel("init"),
                ) ?: return@runOnMainThread
                sendVoid(imageView, sel("setImage:"), nsImage)
                // NSImageScaleNone = 2 — don't scale the badge
                sendVoid(imageView, sel("setImageScaling:"), 2L)

                // Tag for later removal
                sendVoid(imageView, sel("setTag:"), BADGE_TAG)

                // Use auto layout to fill the parent view
                sendVoid(imageView, sel("setTranslatesAutoresizingMaskIntoConstraints:"), false)

                // Add subview (must be in hierarchy before activating constraints)
                sendVoid(targetView, sel("addSubview:"), imageView)

                // Pin all edges to the parent view
                activateConstraint(
                    send(imageView, sel("leadingAnchor")) ?: return@runOnMainThread,
                    send(targetView, sel("leadingAnchor")) ?: return@runOnMainThread,
                )
                activateConstraint(
                    send(imageView, sel("trailingAnchor")) ?: return@runOnMainThread,
                    send(targetView, sel("trailingAnchor")) ?: return@runOnMainThread,
                )
                activateConstraint(
                    send(imageView, sel("topAnchor")) ?: return@runOnMainThread,
                    send(targetView, sel("topAnchor")) ?: return@runOnMainThread,
                )
                activateConstraint(
                    send(imageView, sel("bottomAnchor")) ?: return@runOnMainThread,
                    send(targetView, sel("bottomAnchor")) ?: return@runOnMainThread,
                )

                // Balance retain: alloc gave +1, addSubview retains, so release our ref
                sendVoid(imageView, sel("release"))
                sendVoid(nsImage, sel("release"))

                Logger.d("MacOsTrayBadge") { "Badge overlay updated (pending=$pendingCount)" }
            }
        } catch (e: Exception) {
            Logger.w("MacOsTrayBadge", e) { "Failed to update badge overlay" }
        }
    }

    // --- Internal helpers ---

    /**
     * Navigates from [java.awt.TrayIcon] → CTrayIcon (peer) → AWTTrayIcon (native)
     * → NSStatusItem → NSStatusBarButton or legacy view.
     */
    private fun getStatusBarButton(trayIcon: java.awt.TrayIcon): Pointer? {
        // TrayIcon.peer (CTrayIcon)
        val peerField = java.awt.TrayIcon::class.java.getDeclaredField("peer")
        peerField.isAccessible = true
        val peer = peerField.get(trayIcon) ?: return null

        // CFRetainedResource.ptr (native AWTTrayIcon pointer)
        val ptrField = peer.javaClass.superclass!!.getDeclaredField("ptr")
        ptrField.isAccessible = true
        val nativePtr = ptrField.getLong(peer)
        if (nativePtr == 0L) return null
        val awtTrayIcon = Pointer(nativePtr)

        // [AWTTrayIcon valueForKey:@"theItem"] → NSStatusItem
        val statusItem = send(awtTrayIcon, sel("valueForKey:"), createNSString("theItem") ?: return null)
            ?: return null

        // Try modern API first: [NSStatusItem button] → NSStatusBarButton
        send(statusItem, sel("button"))?.let { return it }

        // Fall back to legacy API: [NSStatusItem view] → AWTTrayIconView
        return send(statusItem, sel("view"))
    }

    /** Removes all subviews with our badge tag from the view. */
    private fun removeTaggedSubviews(view: Pointer) {
        val subviews = send(view, sel("subviews")) ?: return
        val count = sendLong(subviews, sel("count"))

        for (i in (count - 1) downTo 0) {
            val subview = send(subviews, sel("objectAtIndex:"), i) ?: continue
            val tag = sendLong(subview, sel("tag"))
            if (tag == BADGE_TAG) {
                sendVoid(subview, sel("removeFromSuperview"))
            }
        }
    }

    /** Creates an autoreleased NSString from a Kotlin string. */
    private fun createNSString(str: String): Pointer? {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val memory = Memory((bytes.size + 1).toLong())
        memory.write(0, bytes, 0, bytes.size)
        memory.setByte(bytes.size.toLong(), 0)
        return send(cls("NSString") ?: return null, sel("stringWithUTF8String:"), memory)
    }

    /** Creates an NSImage from PNG bytes, setting its point size for HiDPI. */
    private fun createNSImage(pngBytes: ByteArray, pointWidth: Double, pointHeight: Double): Pointer? {
        val memory = Memory(pngBytes.size.toLong())
        memory.write(0, pngBytes, 0, pngBytes.size)

        // [NSData dataWithBytes:length:] (autoreleased)
        val nsData = send(cls("NSData") ?: return null, sel("dataWithBytes:length:"), memory, pngBytes.size.toLong())
            ?: return null

        // [[NSImage alloc] initWithData:] (+1 retain)
        val nsImageClass = cls("NSImage") ?: return null
        val nsImage = send(send(nsImageClass, sel("alloc")) ?: return null, sel("initWithData:"), nsData)
            ?: return null

        // Set point size so macOS uses the 2x pixels on Retina
        val size = NSSize(pointWidth, pointHeight)
        sendVoid(nsImage, sel("setSize:"), size)

        return nsImage
    }

    /** Activates: [anchor1 constraintEqualToAnchor:anchor2].active = YES */
    private fun activateConstraint(anchor1: Pointer, anchor2: Pointer) {
        val constraint = send(anchor1, sel("constraintEqualToAnchor:"), anchor2) ?: return
        sendVoid(constraint, sel("setActive:"), true)
    }
}
