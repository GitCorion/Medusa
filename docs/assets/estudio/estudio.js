/* Estudio Medusa TV. Interacciones mínimas: foco real y profundidad de lectura. */

(function () {
  "use strict";

  /* ---------------------------------------------------------- focus demo */
  var demo = document.querySelector("[data-demo]");

  if (demo) {
    var cards = Array.prototype.slice.call(demo.querySelectorAll(".poster"));
    var title = demo.querySelector("[data-demo-title]");
    var meta = demo.querySelector("[data-demo-meta]");
    var active = 0;

    var select = function (index, moveFocus) {
      if (!cards.length) return;
      active = ((index % cards.length) + cards.length) % cards.length;

      cards.forEach(function (card, position) {
        var isActive = position === active;
        card.classList.toggle("is-active", isActive);
        card.setAttribute("aria-selected", isActive ? "true" : "false");
        card.tabIndex = isActive ? 0 : -1;
      });

      var current = cards[active];
      if (title) title.textContent = current.dataset.title || "";
      if (meta) meta.textContent = current.dataset.meta || "";
      if (moveFocus) current.focus({ preventScroll: true });
    };

    cards.forEach(function (card, position) {
      card.addEventListener("focus", function () {
        select(position, false);
      });
      card.addEventListener("click", function () {
        select(position, true);
      });
    });

    demo.addEventListener("keydown", function (event) {
      if (event.key === "ArrowRight" || event.key === "ArrowDown") {
        event.preventDefault();
        select(active + 1, true);
      } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
        event.preventDefault();
        select(active - 1, true);
      } else if (event.key === "Home") {
        event.preventDefault();
        select(0, true);
      } else if (event.key === "End") {
        event.preventDefault();
        select(cards.length - 1, true);
      }
    });

    select(0, false);
  }

  /* ---------------------------------------------------------- depth rail */
  var depthFill = document.getElementById("depthFill");
  var depthDot = document.getElementById("depthDot");
  var depthSections = Array.prototype.slice.call(document.querySelectorAll("[data-depth]"));
  var ticking = false;

  var updateDepth = function () {
    if (!depthFill || !depthDot) return;

    var marker = window.scrollY + window.innerHeight * 0.42;
    var current = depthSections[0];

    depthSections.forEach(function (section) {
      var top = section.getBoundingClientRect().top + window.scrollY;
      if (top <= marker) current = section;
    });

    var depth = current ? Number(current.dataset.depth) || 0 : 0;
    var fill = Math.min(100, (depth / 900) * 100);

    depthFill.style.height = fill + "%";
    depthDot.style.top = fill + "%";

    ticking = false;
  };

  var requestDepth = function () {
    if (!ticking) {
      ticking = true;
      window.requestAnimationFrame(updateDepth);
    }
  };

  if (depthFill) {
    window.addEventListener("scroll", requestDepth, { passive: true });
    window.addEventListener("resize", requestDepth);
    updateDepth();
  }

  /* ---------------------------------------------------------- current nav */
  var links = Array.prototype.slice.call(document.querySelectorAll(".topnav a"));
  var sections = links
    .map(function (link) {
      var target = link.getAttribute("href");
      return target && target.charAt(0) === "#" ? document.querySelector(target) : null;
    })
    .filter(Boolean);

  if ("IntersectionObserver" in window && sections.length) {
    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;

          links.forEach(function (link) {
            var match = link.getAttribute("href") === "#" + entry.target.id;
            link.classList.toggle("is-current", match);
            if (match) {
              link.setAttribute("aria-current", "location");
            } else {
              link.removeAttribute("aria-current");
            }
          });
        });
      },
      { rootMargin: "-32% 0px -58% 0px", threshold: 0 }
    );

    sections.forEach(function (section) {
      observer.observe(section);
    });
  }
})();
