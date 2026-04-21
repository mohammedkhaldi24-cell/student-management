/**
 * GestionEtu - Main UI Script
 * Navigation, breadcrumbs, sidebar, small UX helpers.
 */
document.addEventListener("DOMContentLoaded", () => {
    const sidebarToggle = document.getElementById("sidebarToggle");
    const sidebar = document.querySelector(".sidebar");
    const sidebarBackdrop = ensureSidebarBackdrop();

    if (sidebarToggle && sidebar) {
        sidebarToggle.addEventListener("click", () => {
            sidebar.classList.toggle("show");
            document.body.classList.toggle("sidebar-open", sidebar.classList.contains("show"));
        });

        document.addEventListener("click", (e) => {
            if (!sidebar.contains(e.target) && !sidebarToggle.contains(e.target)) {
                sidebar.classList.remove("show");
                document.body.classList.remove("sidebar-open");
            }
        });

        if (sidebarBackdrop) {
            sidebarBackdrop.addEventListener("click", () => {
                sidebar.classList.remove("show");
                document.body.classList.remove("sidebar-open");
            });
        }
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

    document.querySelectorAll("form").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (event.defaultPrevented) {
                return;
            }
            const submitter = form.querySelector("button[type='submit']:not([data-no-loading])");
            if (!submitter) {
                return;
            }
            submitter.dataset.originalText = submitter.innerHTML;
            submitter.disabled = true;
            submitter.innerHTML = "<span class=\"spinner-border spinner-border-sm me-2\" aria-hidden=\"true\"></span>Traitement...";
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
    enhanceFileInputs();
});

function ensureSidebarBackdrop() {
    if (document.querySelector(".mobile-sidebar-backdrop")) {
        return document.querySelector(".mobile-sidebar-backdrop");
    }

    const backdrop = document.createElement("div");
    backdrop.className = "mobile-sidebar-backdrop";
    document.body.appendChild(backdrop);
    return backdrop;
}

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

function enhanceFileInputs() {
    document.querySelectorAll("input[type='file']").forEach((input) => {
        if (input.dataset.enhanced === "true") {
            return;
        }
        input.dataset.enhanced = "true";

        const preview = document.createElement("div");
        preview.className = "file-preview-list";
        input.insertAdjacentElement("afterend", preview);

        const render = () => {
            preview.innerHTML = "";
            const files = Array.from(input.files || []);
            if (files.length === 0) {
                return;
            }

            files.forEach((file, index) => {
                const item = document.createElement("div");
                item.className = "file-preview-item";

                const icon = document.createElement("span");
                icon.className = "file-icon";
                icon.innerHTML = `<i class="${fileIconClass(file.name)}"></i>`;

                const name = document.createElement("span");
                name.className = "file-preview-name";
                name.textContent = file.name;

                const meta = document.createElement("span");
                meta.className = "file-preview-meta";
                meta.textContent = formatBytes(file.size);

                const remove = document.createElement("button");
                remove.type = "button";
                remove.className = "file-preview-remove";
                remove.innerHTML = "<i class=\"bi bi-x-lg\"></i>";
                remove.setAttribute("aria-label", "Retirer le fichier");
                remove.addEventListener("click", () => {
                    const transfer = new DataTransfer();
                    files.forEach((candidate, candidateIndex) => {
                        if (candidateIndex !== index) {
                            transfer.items.add(candidate);
                        }
                    });
                    input.files = transfer.files;
                    input.dispatchEvent(new Event("change", { bubbles: true }));
                });

                item.append(icon, name, meta, remove);
                preview.appendChild(item);
            });
        };

        input.addEventListener("change", render);
    });
}

function fileIconClass(name) {
    const lower = (name || "").toLowerCase();
    if (lower.endsWith(".pdf")) return "bi bi-file-earmark-pdf";
    if (lower.match(/\.(png|jpg|jpeg|gif|webp|svg)$/)) return "bi bi-file-earmark-image";
    if (lower.match(/\.(doc|docx)$/)) return "bi bi-file-earmark-word";
    if (lower.match(/\.(xls|xlsx|csv)$/)) return "bi bi-file-earmark-spreadsheet";
    if (lower.match(/\.(zip|rar|7z)$/)) return "bi bi-file-earmark-zip";
    return "bi bi-file-earmark";
}

function formatBytes(bytes) {
    if (!bytes || bytes <= 0) {
        return "0 KB";
    }
    const units = ["B", "KB", "MB", "GB"];
    const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / Math.pow(1024, index)).toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}
