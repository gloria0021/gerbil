(() => {
  const root = document.documentElement;

  /* ------------------------------------------------------------------
     1. Color Mode (Light / Dark) with system preference fallback
     ------------------------------------------------------------------ */
  const MODE_KEY = 'gerbil-site-mode';
  const mqDark = window.matchMedia('(prefers-color-scheme: dark)');

  function applyMode(mode) {
    root.dataset.mode = mode;
    const btn = document.getElementById('mode-toggle');
    if (btn) {
      const isDark = mode === 'dark';
      btn.setAttribute('aria-pressed', String(isDark));
      btn.setAttribute('aria-label', isDark ? 'ライトモードに切り替え' : 'ダークモードに切り替え');
      const sun = btn.querySelector('[data-icon="sun"]');
      const moon = btn.querySelector('[data-icon="moon"]');
      if (sun && moon) {
        sun.style.display = isDark ? 'block' : 'none';
        moon.style.display = isDark ? 'none' : 'block';
      }
    }
  }

  const savedMode = (() => {
    try { return localStorage.getItem(MODE_KEY); } catch { return null; }
  })();
  applyMode(savedMode || (mqDark.matches ? 'dark' : 'light'));

  mqDark.addEventListener('change', (e) => {
    try {
      if (!localStorage.getItem(MODE_KEY)) applyMode(e.matches ? 'dark' : 'light');
    } catch {
      applyMode(e.matches ? 'dark' : 'light');
    }
  });

  document.getElementById('mode-toggle')?.addEventListener('click', () => {
    const next = root.dataset.mode === 'dark' ? 'light' : 'dark';
    applyMode(next);
    try { localStorage.setItem(MODE_KEY, next); } catch { /* ignore */ }
  });

  /* ------------------------------------------------------------------
     2. Interactive Palette Preview (Suna / Canyon / Fog / Olive / Obsidian)
     ------------------------------------------------------------------ */
  const swatchButtons = document.querySelectorAll('[data-set-palette]');
  if (swatchButtons.length) {
    swatchButtons.forEach((btn) => {
      btn.addEventListener('click', () => {
        const palette = btn.dataset.setPalette;
        if (palette === 'suna') {
          delete root.dataset.palette;
        } else {
          root.dataset.palette = palette;
        }
        swatchButtons.forEach((b) => b.setAttribute('aria-pressed', String(b === btn)));
      });
    });
  }

  /* ------------------------------------------------------------------
     3. Showcase Tabs (Reading / Boards / Thread / URL)
     ------------------------------------------------------------------ */
  const tabs = document.querySelectorAll('[data-showcase-tab]');
  const showcaseImg = document.getElementById('showcase-img');
  const showcaseCaption = document.getElementById('showcase-caption');

  if (tabs.length && showcaseImg) {
    tabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        tabs.forEach((t) => t.setAttribute('aria-selected', String(t === tab)));
        showcaseImg.src = tab.dataset.src;
        showcaseImg.alt = tab.dataset.alt || '';
        if (showcaseCaption) showcaseCaption.textContent = tab.dataset.caption || '';
      });
    });
  }

  /* ------------------------------------------------------------------
     4. Native <dialog> Screenshot Lightbox
     ------------------------------------------------------------------ */
  const dialog = document.getElementById('lightbox');
  const dialogImg = document.getElementById('lightbox-img');
  const dialogClose = document.getElementById('lightbox-close');

  if (dialog && dialogImg) {
    document.querySelectorAll('[data-lightbox-src]').forEach((trigger) => {
      trigger.addEventListener('click', () => {
        dialogImg.src = trigger.dataset.lightboxSrc;
        dialogImg.alt = trigger.dataset.lightboxAlt || '';
        dialog.showModal();
      });
    });

    dialogClose?.addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) dialog.close();
    });
  }

  /* ------------------------------------------------------------------
     5. Privacy Policy Language Filter (JA / EN / Both)
     ------------------------------------------------------------------ */
  const langBtns = document.querySelectorAll('[data-policy-lang]');
  const jaSection = document.getElementById('ja');
  const enSection = document.getElementById('en');

  if (langBtns.length && jaSection && enSection) {
    function setPolicyLang(lang, updateHash) {
      langBtns.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.policyLang === lang)));
      if (lang === 'ja') {
        jaSection.hidden = false;
        enSection.hidden = true;
      } else if (lang === 'en') {
        jaSection.hidden = true;
        enSection.hidden = false;
      } else {
        jaSection.hidden = false;
        enSection.hidden = false;
      }
      if (updateHash && (lang === 'ja' || lang === 'en')) {
        history.replaceState(null, '', '#' + lang);
      }
    }

    langBtns.forEach((btn) => {
      btn.addEventListener('click', () => setPolicyLang(btn.dataset.policyLang, true));
    });

    const hash = window.location.hash.replace('#', '').toLowerCase();
    if (hash === 'en' || hash === 'english') {
      setPolicyLang('en', false);
    } else if (hash === 'ja' || hash === '日本語') {
      setPolicyLang('ja', false);
    }
  }

  /* ------------------------------------------------------------------
     6. Scroll Reveal (IntersectionObserver)
     ------------------------------------------------------------------ */
  const reveals = document.querySelectorAll('.reveal');
  if (reveals.length && 'IntersectionObserver' in window) {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -32px 0px' }
    );
    reveals.forEach((el) => io.observe(el));
  } else {
    reveals.forEach((el) => el.classList.add('is-visible'));
  }
})();
