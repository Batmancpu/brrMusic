## Zenith Canvas Web Guide

### 🎬 Make your own Canvas
- 1️⃣ **Open the website:** go to https://zenith-canvas.vercel.app/ in your browser.
- 2️⃣ **Sign in:** open the **Me** tab and tap **Sign in with Telegram Bot**. Telegram opens, press **Start** in the bot chat, then return to the website. __Only Zenith group members can sign in.__
- 3️⃣ **Prepare your video:** MP4, square (1:1), 720 to 1280 px, 3 to 8 seconds, no audio, max `3 MB`.
- 4️⃣ **Upload:** open the **Upload** tab and choose your video. The checks turn green when it fits all requirements.
- 5️⃣ **Paste song info:** in the Zenith app, copy the song info, then tap **Paste Song Info from App**. Title, artist and album fill in automatically.
- 6️⃣ **Post:** tap **Post**. Limit: 3 canvases per day.

### ⏳ Approval and result
- 7️⃣ **Wait a few minutes:** an admin reviews your canvas in the Zenith group.
- 8️⃣ **Check the status:** open the **Me** tab. You will see **Pending**, **Approved** or **Rejected**. You can delete your own uploads there.
- 9️⃣ **Use it in the app:** once approved, play the song in Zenith and your canvas shows up. If it is not there yet, wait a few minutes and reopen the app.

### 💡 Tips
- Use a looping clip so the canvas feels smooth.
- Make sure the title and artist match the song in the app.
- Like your favorite canvases from the **Explore** tab to support other creators.

### Discover
- New releases and Charts pages, with shortcut cards on the New tab.
- Search category cards are shuffled once per app launch.
- Explore shortcut cards are now clearly visible in the light theme.

### Library and albums
- Liked songs can be kept downloaded.
- Album headers show an Apple Hi-Res Lossless badge, and the quality label includes a lossless icon.
- High-resolution YouTube covers, a smoother player cover hold-over, and per-track covers for local files.
- Album pages tint from the dominant hue when the edge colour averages to grey.

### Player and queue
- Long-press a queue row to open its track menu.
- The queue swipe action has a new iOS-style design.
- The play/pause button responds to taps immediately and morphs faster, and its loading animation now runs as a sheen on the seek bar.
- Control icons are thinner, and the AirPlay triangle has a wider base.
- Mesh backdrops are softer for white covers and blend the cover's seam colours, ignoring letterbox bars.
- Video tracks with still covers keep a square mini player cover and can use full-bleed artwork.

### Glass interface
- The glass nav bar and player are shorter, with more realistic edge refraction, and the nav bar size can be adjusted manually.
- The glass tab indicator has a liquid rubber-band stretch at the pill ends and stays confined within the pill.
- Tapping the active tab scrolls smoothly to the top.
- The search field expands to full width while the keyboard is open, and the Home circle fades out.
- Panel mini rows now match the glass nav bar player.

### App icon and splash
- New Dark Glass launcher icon option.
- The splash screen uses an Apple Music style gradient with the dark glass Z logo, drawn from the first frame.

### Performance
- Minimizing and expanding the player is much smoother on songs with a canvas when the legacy mesh backdrop is enabled.
- Incoming AutoMix artwork, backdrop and canvas are prefetched, and the skip crossfade arms faster, so the next button responds quicker.
- Track analysis runs at background priority unless performance mode is on.
- Canvas stills are allocated off the main thread.
- Mid-track quality upgrades are skipped during the last 45 seconds of a track.

### Fixes and polish
- Fixed playlist playback order, notification transport slots, and Latin accent matching in lyrics.
- Fixed the queue not scrolling the last track fully into view, and neighbouring rows reappearing after a swipe removal.
- Fixed the mini player jump when the search field collapses.
- Fixed the Play and Shuffle labels clipping in the release header.
- Removed dead code and unused imports.
