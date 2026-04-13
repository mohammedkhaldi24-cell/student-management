/**
 * GestionEtu - Main UI Script
 * Navigation, breadcrumbs, sidebar, small UX helpers.
 */
document.addEventListener("DOMContentLoaded", () => {
    const sidebarToggle = document.getElementById("sidebarToggle");
    const sidebar = document.querySelector(".sidebar");

    if (sidebarToggle && sidebar) {
        sidebarToggle.addEventListener("click", () => {
            sidebar.classList.toggle("show");
        });

        document.addEventListener("click", (e) => {
            if (!sidebar.contains(e.target) && !sidebarToggle.contains(e.target)) {
                sidebar.classList.remove("show");
            }
        });
    }

    document.querySelectorAll(".alert.alert-success, .alert.alert-info").forEach((alert) => {
        setTimeout(() => {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            if (bsAlert) {
                bsAlert.close();
            }
        }, 5000);
    });

    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (e) => {
            const message = form.getAttribute("data-confirm") || "Confirmer cette action ?";
            if (!confirm(message)) {
                e.preventDefault();
            }
        });
    });

    document.querySelectorAll("[data-bs-toggle='tooltip']").forEach((el) => new bootstrap.Tooltip(el));
    document.querySelectorAll("[data-bs-toggle='popover']").forEach((el) => new bootstrap.Popover(el));

    window.scrollToSection = function (id) {
        const section = document.getElementById(id);
        if (section) {
            section.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    };

    document.querySelectorAll("input.note-input").forEach((input) => {
        input.addEventListener("change", function () {
            const val = parseFloat(this.value);
            if (!isNaN(val)) {
                const bounded = Math.max(0, Math.min(20, val));
                this.value = Math.round(bounded * 4) / 4;
            }
        });
    });

    markActiveSidebarLink();
    initBreadcrumb();
    initTopbarAvatar();
    initNotificationBadge();
});

function markActiveSidebarLink() {
    const currentPath = window.location.pathname;
    let bestMatch = null;
    let bestLength = -1;

    document.querySelectorAll(".sidebar-menu a").forEach((link) => {
        const href = link.getAttribute("href");
        if (!href || href === "#") {
            return;
        }
        if (currentPath === href || currentPath.startsWith(href + "/")) {
            if (href.length > bestLength) {
                bestLength = href.length;
                bestMatch = link;
            }
        }
    });

    if (bestMatch) {
        bestMatch.classList.add("active");
    }
}

function initBreadcrumb() {
    const breadcrumb = document.getElementById("pageBreadcrumb");
    if (!breadcrumb) {
        return;
    }

    const path = window.location.pathname.split("/").filter(Boolean);
    const currentTitle = document.querySelector(".page-breadcrumb h5")?.textContent?.trim() || "Page";

    const homeHref = path.length > 0 ? `/${path[0]}/dashboard` : "/";
    const items = [{ label: "Accueil", href: homeHref }];

    if (path.length > 0) {
        const root = path[0];
        const rootLabelMap = {
            admin: "Admin",
            chef: "Chef Filiere",
            teacher: "Enseignant",
            student: "Etudiant"
        };
        items.push({
            label: rootLabelMap[root] || capitalize(root),
            href: "/" + root + "/dashboard"
        });
    }

    items.push({ label: currentTitle, href: null });

    breadcrumb.innerHTML = "";
    items.forEach((item, idx) => {
        const li = document.createElement("li");
        const isLast = idx === items.length - 1;
        li.className = "breadcrumb-item" + (isLast ? " active" : "");
        if (isLast || !item.href) {
            li.textContent = item.label;
            li.setAttribute("aria-current", "page");
        } else {
            const a = document.createElement("a");
            a.href = item.href;
            a.textContent = item.label;
            li.appendChild(a);
        }
        breadcrumb.appendChild(li);
    });
}

function initTopbarAvatar() {
    const usernameEl = document.getElementById("topbarUsername");
    const avatarInitialEl = document.getElementById("topbarAvatarInitial");
    if (!usernameEl || !avatarInitialEl) {
        return;
    }

    const username = (usernameEl.textContent || "").trim();
    if (!username) {
        avatarInitialEl.textContent = "U";
        return;
    }

    const tokens = username.split(/[.\s_-]+/).filter(Boolean);
    if (tokens.length >= 2) {
        avatarInitialEl.textContent = (tokens[0][0] + tokens[1][0]).toUpperCase();
    } else {
        avatarInitialEl.textContent = username.substring(0, 2).toUpperCase();
    }
}

function initNotificationBadge() {
    const badge = document.getElementById("notificationCount");
    if (!badge) {
        return;
    }
    const count = parseInt((badge.textContent || "0").trim(), 10);
    if (isNaN(count) || count <= 0) {
        badge.style.display = "none";
    }
}

function capitalize(value) {
    if (!value) {
        return "";
    }
    return value.charAt(0).toUpperCase() + value.slice(1);
}
