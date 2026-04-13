/**
 * GestionEtu - Script principal
 * Plateforme de Gestion des Notes et Absences
 */

document.addEventListener('DOMContentLoaded', function () {

    // ── Toggle Sidebar (responsive) ──────────────────────────
    const sidebarToggle = document.getElementById('sidebarToggle');
    const sidebar = document.querySelector('.sidebar');

    if (sidebarToggle && sidebar) {
        sidebarToggle.addEventListener('click', function () {
            sidebar.classList.toggle('show');
        });
        // Fermer sidebar en cliquant en dehors
        document.addEventListener('click', function (e) {
            if (!sidebar.contains(e.target) && !sidebarToggle.contains(e.target)) {
                sidebar.classList.remove('show');
            }
        });
    }

    // ── Auto-dismiss des alertes après 5s ───────────────────
    const alerts = document.querySelectorAll('.alert.alert-success, .alert.alert-info');
    alerts.forEach(function (alert) {
        setTimeout(function () {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            if (bsAlert) bsAlert.close();
        }, 5000);
    });

    // ── Confirmation de suppression ──────────────────────────
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
        form.addEventListener('submit', function (e) {
            const msg = form.getAttribute('data-confirm') || 'Confirmer cette action ?';
            if (!confirm(msg)) e.preventDefault();
        });
    });

    // ── Activer les tooltips Bootstrap ──────────────────────
    const tooltipEls = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    tooltipEls.forEach(el => new bootstrap.Tooltip(el));

    // ── Activer les popovers Bootstrap ──────────────────────
    const popoverEls = document.querySelectorAll('[data-bs-toggle="popover"]');
    popoverEls.forEach(el => new bootstrap.Popover(el));

    // ── Scroll vers une section ──────────────────────────────
    window.scrollToSection = function (id) {
        const el = document.getElementById(id);
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };

    // ── Validation des formulaires de notes ─────────────────
    document.querySelectorAll('input.note-input').forEach(function (input) {
        input.addEventListener('change', function () {
            const val = parseFloat(this.value);
            if (!isNaN(val)) {
                if (val < 0) this.value = 0;
                if (val > 20) this.value = 20;
                // Arrondir au quart le plus proche
                this.value = Math.round(val * 4) / 4;
            }
        });
    });

    // ── Marquer lien actif dans le sidebar ──────────────────
    const currentPath = window.location.pathname;
    document.querySelectorAll('.sidebar-menu a').forEach(function (link) {
        if (link.getAttribute('href') && currentPath.startsWith(link.getAttribute('href'))) {
            link.classList.add('active');
        }
    });

    // ── Animation des cartes statistiques ───────────────────
    const statValues = document.querySelectorAll('.stat-value');
    statValues.forEach(function (el) {
        const finalVal = parseInt(el.textContent);
        if (!isNaN(finalVal) && finalVal > 0) {
            let current = 0;
            const step = Math.ceil(finalVal / 30);
            const timer = setInterval(function () {
                current = Math.min(current + step, finalVal);
                el.textContent = current;
                if (current >= finalVal) clearInterval(timer);
            }, 30);
        }
    });

});
