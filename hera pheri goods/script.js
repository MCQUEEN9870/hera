// Dropdown Position Adjustment
document.addEventListener("DOMContentLoaded", function () {
    // Initialize AOS (Animate On Scroll) library
    AOS.init({
        once: false,     // whether animation should happen only once - while scrolling down
        mirror: true,    // whether elements should animate out while scrolling past them
        duration: 1000   // values from 0 to 3000, with step 50ms
    });

    // Get dropdown elements
    const dropdown = document.querySelector(".dropdown");
    const dropdownContent = document.querySelector(".dropdown-content");

    if (dropdown && dropdownContent) {
        dropdown.addEventListener("mouseseeker", function () {
            // Calculate available space on the right side
            const rect = dropdown.getBoundingClientRect();
            const rightSpace = window.innerWidth - rect.left;

            // Adjust dropdown position if not enough space on the right
            if (rightSpace < 400) {
                dropdownContent.style.left = "auto";
                dropdownContent.style.right = "0";
            }
        });
    }
});

// Glass navbar scroll behavior (home page only)
document.addEventListener('DOMContentLoaded', function() {
    const glassNav = document.querySelector('.glass-nav');
    if (!glassNav) return;
    // ensure dropdown stays attached to viewport under fixed nav on resize
    const dropdownContent = document.querySelector('.glass-nav .dropdown .dropdown-content');
    const toggleScrolled = () => {
        if (window.scrollY > 40) glassNav.classList.add('scrolled');
        else glassNav.classList.remove('scrolled');
    };
    toggleScrolled();
    window.addEventListener('scroll', toggleScrolled, { passive: true });
    window.addEventListener('resize', () => {
        if (!dropdownContent) return;
        if (window.innerWidth <= 768) {
            dropdownContent.style.position = 'fixed';
            dropdownContent.style.top = (glassNav.offsetHeight) + 'px';
            dropdownContent.style.right = '12px';
            dropdownContent.style.left = 'auto';
        } else {
            dropdownContent.style.position = '';
            dropdownContent.style.top = '';
            dropdownContent.style.right = '';
            dropdownContent.style.left = '';
        }
    });

    // Mobile sidebar toggle
    const mobileBtn = document.querySelector('.mobile-menu-btn');
    const mobileSidebar = document.querySelector('.mobile-sidebar');
    const mobileOverlay = document.querySelector('.mobile-overlay');
    const mobileClose = document.querySelector('.mobile-close');

    const closeMobileMenu = () => {
        if (mobileSidebar) mobileSidebar.classList.remove('open');
        if (mobileOverlay) mobileOverlay.classList.remove('active');
        document.body.style.overflow = '';
        if (mobileBtn) mobileBtn.classList.remove('opened');
    };

    const openMobileMenu = () => {
        if (mobileSidebar) mobileSidebar.classList.add('open');
        if (mobileOverlay) mobileOverlay.classList.add('active');
        document.body.style.overflow = 'hidden';
    };

    if (mobileBtn && mobileSidebar && mobileOverlay) {
        mobileBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            if (mobileSidebar.classList.contains('open')) {
                closeMobileMenu();
            } else {
                openMobileMenu();
            }
        });
        mobileOverlay.addEventListener('click', closeMobileMenu);
    }

    if (mobileClose) {
        mobileClose.addEventListener('click', (e) => {
            e.stopPropagation();
            closeMobileMenu();
        });
    }

    // Close menu on link click and ensure profile link visibility syncs
    document.querySelectorAll('.mobile-nav-item').forEach(link => {
        link.addEventListener('click', closeMobileMenu);
    });

    // Sync My Profile visibility with login state for sidebar too
    const sidebarProfileLink = document.getElementById('profileLink');
    const isLoggedInSidebar = localStorage.getItem('isLoggedIn') === 'true' || !!localStorage.getItem('userContact');
    if (sidebarProfileLink) {
        sidebarProfileLink.style.display = isLoggedInSidebar ? 'inline-block' : 'none';
    }
});

// Navigation Item Click Effects
document.querySelectorAll(".nav-item").forEach((item) => {
    // Add click effect with ripple animation
    item.addEventListener("click", () => {
        item.classList.add("active");

        // Create ripple effect element
        const ripple = document.createElement("span");
        ripple.classList.add("ripple");
        ripple.style.left = "50%";
        ripple.style.top = "50%";
        item.appendChild(ripple);

        // Remove ripple effect after animation
        setTimeout(() => {
            ripple.remove();
        }, 1000);
    });

    // Remove active class when mouse leaves
    item.addEventListener("mouseleave", () => {
        item.classList.remove("active");
    });
});

// NOTE: Removed a duplicate mobile-only toggle that was also toggling the
// dropdown on click. On real devices the two handlers fired and canceled
// each other (open then immediately close). The unified handler below
// works for both desktop and mobile.

// ==========================================
// Truck Type Dropdown Toggle
// ==========================================
document.addEventListener('DOMContentLoaded', function() {
    const truckTypeBtn = document.querySelector('.truck-type-btn');
    const dropdown = document.querySelector('.dropdown');

    if (!truckTypeBtn || !dropdown) return;

    // Toggle dropdown on truck type button tap/click (works on mobile + desktop)
    const onToggle = (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropdown.classList.toggle('active');
    };
    truckTypeBtn.addEventListener('click', onToggle, { passive: false });
    truckTypeBtn.addEventListener('touchstart', onToggle, { passive: false });

    // Prevent clicks inside dropdown from bubbling to document (so it doesn't close immediately)
    const dropdownContent = dropdown.querySelector('.dropdown-content');
    if (dropdownContent) {
        dropdownContent.addEventListener('click', (e) => e.stopPropagation());
        dropdownContent.addEventListener('touchstart', (e) => e.stopPropagation(), { passive: true });
    }

    // Close dropdown when clicking/tapping outside
    const closeIfOutside = (e) => {
        if (!dropdown.contains(e.target)) {
            dropdown.classList.remove('active');
        }
    };
    document.addEventListener('click', closeIfOutside, { passive: true });
    document.addEventListener('touchstart', closeIfOutside, { passive: true });
});

// ==========================================
// Logo Animation using GSAP
// ==========================================
gsap.from(".logo", { 
    duration: 2,
    opacity: 0,
    y: -50,
    ease: "power2.out"
});

// GSAP Animation for Main Content Block
gsap.to(".registration-block", {
    duration: 0.5,
    opacity: 1,
    ease: "power2.in",
    onComplete: () => {
        // Animate the registration section
        gsap.to(".registration-section", {
            duration: 0.5,
            opacity: 1,
            y: 0,
            ease: "power2.out",
            onComplete: () => {
                // Animate the find vehicle section
                gsap.to(".find-vehicle-section", {
                    duration: 0.5,
                    opacity: 1,
                    y: 0,
                    ease: "power2.out"
                });
            }
        });
    }
});



// Floating Panel Functionality
document.addEventListener('DOMContentLoaded', function() {
    const floatingPanel = document.querySelector('.floating-panel');
    const panelToggle = document.querySelector('.panel-toggle');
    const closePanel = document.querySelector('.close-panel');
    let isExpanded = false;
    let isTransitioning = false;

    // Toggle panel on click
    panelToggle.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        
        if (isTransitioning) return; // Prevent multiple clicks during transition
        
        isTransitioning = true;
        if (!isExpanded) {
            floatingPanel.classList.add('active');
            isExpanded = true;
        } else {
            floatingPanel.classList.remove('active');
            isExpanded = false;
        }
        
        // Reset transitioning state after animation completes
        setTimeout(() => {
            isTransitioning = false;
        }, 400); // Match this with CSS transition duration
    });

    // Close panel
    closePanel.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        
        if (isTransitioning) return;
        
        isTransitioning = true;
        floatingPanel.classList.remove('active');
        isExpanded = false;
        
        setTimeout(() => {
            isTransitioning = false;
        }, 400);
    });

    // Close panel when clicking outside
    document.addEventListener('click', (e) => {
        if (isExpanded && !floatingPanel.contains(e.target)) {
            if (isTransitioning) return;
            
            isTransitioning = true;
            floatingPanel.classList.remove('active');
            isExpanded = false;
            
            setTimeout(() => {
                isTransitioning = false;
            }, 400);
        }
    });

    // Prevent panel from closing when clicking inside
    floatingPanel.addEventListener('click', (e) => {
        e.stopPropagation();
    });

    // Handle escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && isExpanded) {
            if (isTransitioning) return;
            
            isTransitioning = true;
            floatingPanel.classList.remove('active');
            isExpanded = false;
            
            setTimeout(() => {
                isTransitioning = false;
            }, 400);
        }
    });

});

document.addEventListener("DOMContentLoaded", function() {
    const toggleIcons = document.querySelectorAll('.toggle-icon');

    toggleIcons.forEach(icon => {
        icon.addEventListener('click', function() {
            const answer = this.parentElement.nextElementSibling;
            if (answer.style.display === "none") {
                answer.style.display = "block";
                this.classList.remove('fa-chevron-down');
                this.classList.add('fa-chevron-up');
            } else {
                answer.style.display = "none";
                this.classList.remove('fa-chevron-up');
                this.classList.add('fa-chevron-down');
            }
        });
    });
});

// Vehicle Category Flip Cards Enhancement
document.addEventListener('DOMContentLoaded', function() {
    const flipCards = document.querySelectorAll('.flip-card');
    const truckTypeBtn = document.querySelector('.truck-type-btn');
    const dropdown = document.querySelector('.dropdown');
    
    // Add touch support for mobile devices
    flipCards.forEach(card => {
        let touchStartTime = 0;
        let touchEndTime = 0;
        
        card.addEventListener('touchstart', function() {
            touchStartTime = new Date().getTime();
        });
        
        card.addEventListener('touchend', function() {
            touchEndTime = new Date().getTime();
            
            // If it's a quick tap (less than 300ms), flip the card
            if (touchEndTime - touchStartTime < 300) {
                const cardInner = this.querySelector('.flip-card-inner');
                const isFlipped = cardInner.style.transform === 'rotateY(180deg)';
                
                if (isFlipped) {
                    cardInner.style.transform = 'rotateY(0deg)';
                } else {
                    cardInner.style.transform = 'rotateY(180deg)';
                }
            }
        });
        
        // Setup image rotation for each card
        setupImageRotation(card);
    });
    
    // Add animation when cards come into view
    const observerOptions = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1
    };
    
    const cardObserver = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'translateY(0)';
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);
    
    // Set initial state and observe each card
    flipCards.forEach(card => {
        card.style.opacity = '0';
        card.style.transform = 'translateY(30px)';
        card.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
        cardObserver.observe(card);
    });
    
    // Handle View button clicks
    const viewButtons = document.querySelectorAll('.card-btn');
    viewButtons.forEach(button => {
        button.addEventListener('click', function(e) {
            e.preventDefault();
            
            // Get the category from the data attribute
            const category = this.getAttribute('data-category');
            
            // Scroll to top of page first
            window.scrollTo({ top: 0, behavior: 'smooth' });
            
            // Wait for the scroll to complete before opening dropdown
            setTimeout(() => {
                // Get the truck type button
                const truckTypeBtn = document.querySelector('.truck-type-btn');
                
                // Simulate click on the truck type button to open dropdown
                if (truckTypeBtn) {
                    truckTypeBtn.click();
                    
                    // After dropdown is open, scroll to the appropriate category
                    setTimeout(() => {
                        const categoryElement = findCategoryElement(category);
                        if (categoryElement) {
                            // Smooth scroll to the category
                            categoryElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            // Highlight the category temporarily
                            highlightCategory(categoryElement);
                        }
                    }, 300);
                }
            }, 500);
        });
    });
    
    // Function to find the appropriate category in the dropdown
    function findCategoryElement(category) {
        const dropdownContent = document.querySelector('.dropdown-content');
        const categories = dropdownContent.querySelectorAll('.category');
        
            // Map card categories to dropdown categories
    const categoryMap = {
        'small': 'Small Transport Vehicles',
        'medium': 'Light Transport Vehicles',
        'heavy': 'Medium & Heavy Transport Vehicles',
        'special': 'Special Utility Vehicles',
        'packers': 'Service-Based Vehicles',
        'delivery': 'Service-Based Vehicles',
        'food': 'Food & Beverage Vehicles',
        'emergency': 'Emergency Vehicles'
    };
        
        const targetCategory = categoryMap[category];
        
        // Find the category with matching text
        for (const categoryElem of categories) {
            const heading = categoryElem.querySelector('h3');
            if (heading && heading.textContent.includes(targetCategory)) {
                return categoryElem;
            }
        }
        
        return null;
    }
    
    // Function to highlight a category temporarily
    function highlightCategory(element) {
        const originalBackground = element.style.backgroundColor;
        const originalTransition = element.style.transition;
        
        element.style.transition = 'background-color 0.5s ease';
        element.style.backgroundColor = 'rgba(255, 165, 0, 0.2)';
        
        setTimeout(() => {
            element.style.backgroundColor = originalBackground;
            setTimeout(() => {
                element.style.transition = originalTransition;
            }, 500);
        }, 1500);
    }
    
    // Function to setup image rotation for a card
    function setupImageRotation(card) {
        const images = card.querySelectorAll('.image-container img');
        if (images.length <= 1) return;
        
        let currentIndex = 0;
        
        // Find the initially active image
        for (let i = 0; i < images.length; i++) {
            if (images[i].classList.contains('active')) {
                currentIndex = i;
                break;
            }
        }
        
        // Rotate images every 5 seconds
        setInterval(() => {
            // Remove active class from current image
            images[currentIndex].classList.remove('active');
            
            // Move to next image
            currentIndex = (currentIndex + 1) % images.length;
            
            // Add active class to next image
            images[currentIndex].classList.add('active');
        }, 5000);
    }
});

// Review Sidebar Functionality
document.addEventListener('DOMContentLoaded', function() {
    const reviewSidebar = document.querySelector('.review-sidebar');
    const reviewToggle = document.querySelector('.review-toggle');
    const stars = document.querySelectorAll('.stars i');
    const ratingText = document.querySelector('.rating-text');
    const ratingDescs = document.querySelectorAll('.rating-desc');
    const submitButton = document.querySelector('.submit-review');
    const reviewTextarea = document.querySelector('.review-form textarea');
    const userInfoModal = document.getElementById('userInfoModal');
    const successModal = document.getElementById('successModal');
    const closeModals = document.querySelectorAll('.close-modal');
    const submitUserInfoBtn = document.getElementById('submitUserInfo');
    const okButton = document.getElementById('okButton');
    let currentRating = 0;

    // Toggle sidebar
    reviewToggle.addEventListener('click', function(e) {
        e.stopPropagation();
        reviewSidebar.classList.toggle('active');
    });

    // Close sidebar when clicking outside
    document.addEventListener('click', function(e) {
        if (!reviewSidebar.contains(e.target) && !reviewToggle.contains(e.target)) {
            reviewSidebar.classList.remove('active');
        }
    });

    // Star rating functionality
    stars.forEach((star, index) => {
        star.addEventListener('click', function() {
            currentRating = index + 1;
            updateRating(currentRating);
        });

        star.addEventListener('mouseover', function() {
            const rating = index + 1;
            highlightStars(rating);
        });

        star.addEventListener('mouseleave', function() {
            highlightStars(currentRating);
        });
    });

    // Update rating display
    function updateRating(rating) {
        // Update stars
        stars.forEach((star, index) => {
            star.classList.toggle('active', index < rating);
        });

        // Update text
        ratingText.textContent = rating ? `You rated ${rating} stars` : 'Click to rate';

        // Update description
        ratingDescs.forEach(desc => {
            desc.classList.toggle('active', 
                desc.getAttribute('data-rating') === String(rating));
        });

        // Enable/disable submit button
        submitButton.disabled = !rating;
    }

    // Highlight stars on hover
    function highlightStars(rating) {
        stars.forEach((star, index) => {
            star.style.color = index < rating ? '#ffd700' : '#ddd';
        });
    }

    // Close modals when clicking on X
    closeModals.forEach(closeBtn => {
        closeBtn.addEventListener('click', function() {
            userInfoModal.style.display = 'none';
            successModal.style.display = 'none';
        });
    });

    // Close modals when clicking outside
    window.addEventListener('click', function(e) {
        if (e.target === userInfoModal) {
            userInfoModal.style.display = 'none';
        }
        if (e.target === successModal) {
            successModal.style.display = 'none';
        }
    });

    // OK button on success modal
    okButton.addEventListener('click', function() {
        successModal.style.display = 'none';
        reviewSidebar.classList.remove('active');
        // Reset the form
        reviewTextarea.value = '';
        currentRating = 0;
        updateRating(0);
    });

    // Handle review submission
    submitButton.addEventListener('click', function() {
        // Check if user is logged in
        const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
        const reviewText = reviewTextarea.value;

        if (isLoggedIn) {
            // For signed-in users, use their existing info from localStorage
            const userPhone = localStorage.getItem('userPhone') || localStorage.getItem('contactNumber');
            
            console.log("User is logged in, phone from storage:", userPhone);
            
            // Make AJAX call to save feedback in the users table
            saveFeedbackForSignedInUser(userPhone, currentRating, reviewText);
            
            // Show success message
            successModal.style.display = 'block';
        } else {
            // For non-signed-in users, show modal to collect name and address
            userInfoModal.style.display = 'block';
        }
    });

    // Handle user info submission for non-signed-in users
    submitUserInfoBtn.addEventListener('click', function() {
        const userName = document.getElementById('userName').value;
        const userAddress = document.getElementById('userAddress').value;
        const reviewText = reviewTextarea.value;

        if (!userName || !userAddress) {
            alert('Please enter both your name and address.');
            return;
        }

        // Save feedback in the feedback table for non-signed-in users
        saveFeedbackForNonSignedInUser(userName, userAddress, currentRating, reviewText);
        
        // Hide user info modal
        userInfoModal.style.display = 'none';
        
        // Show success message
        successModal.style.display = 'block';
    });

    // Function to save feedback for signed-in users (to users table)
    function saveFeedbackForSignedInUser(userPhone, rating, reviewText) {
        console.log('Attempting to save feedback with phone:', userPhone, 'Rating:', rating);
        
        if (!userPhone) {
            console.error('Cannot save feedback: No phone number available');
            alert('Error: Could not retrieve your contact information. Please log in again.');
            return;
        }
        
        // Call backend with absolute API base (works on localhost and production)
        const base = window.API_BASE_URL || 'http://localhost:8080';
        fetch(`${base}/api/save-user-feedback`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({
                phone: userPhone,
                rating: rating,
                review_text: reviewText
            })
        })
        .then(response => {
            console.log('Response status:', response.status, response.statusText);
            if (!response.ok) {
                return response.text().then(text => {
                    console.error('Error response:', text);
                    throw new Error('Network response was not ok: ' + response.status);
                });
            }
            return response.json();
        })
        .then(data => {
            console.log('Feedback saved successfully:', data);
        })
        .catch(error => {
            console.error('Error saving feedback:', error);
            // Try direct AJAX call with XMLHttpRequest as fallback
            const xhr = new XMLHttpRequest();
            xhr.open('POST', (window.API_BASE_URL ? `${window.API_BASE_URL}/api/save-user-feedback` : 'http://localhost:8080/api/save-user-feedback'), true);
            xhr.setRequestHeader('Content-Type', 'application/json');
            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    console.log('XHR Status:', xhr.status);
                    console.log('XHR Response:', xhr.responseText);
                }
            };
            xhr.send(JSON.stringify({
                phone: userPhone,
                rating: rating,
                review_text: reviewText
            }));
        });

        // For development testing:
        console.log('Request sent to server - Phone:', userPhone, 'Rating:', rating, 'Review:', reviewText);
    }

    // Function to save feedback for non-signed-in users (to feedback table)
    function saveFeedbackForNonSignedInUser(name, address, rating, reviewText) {
        console.log('Attempting to save non-user feedback with name:', name, 'Rating:', rating);
        
        // Call backend with absolute API base (works on localhost and production)
        const base = window.API_BASE_URL || 'http://localhost:8080';
        fetch(`${base}/api/save-feedback`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({
                name: name,
                address: address,
                rating: rating,
                review_text: reviewText
            })
        })
        .then(response => {
            console.log('Response status:', response.status, response.statusText);
            if (!response.ok) {
                return response.text().then(text => {
                    console.error('Error response:', text);
                    throw new Error('Network response was not ok: ' + response.status);
                });
            }
            return response.json();
        })
        .then(data => {
            console.log('Feedback saved successfully:', data);
        })
        .catch(error => {
            console.error('Error saving feedback:', error);
            // Try direct AJAX call with XMLHttpRequest as fallback
            const xhr = new XMLHttpRequest();
            xhr.open('POST', (window.API_BASE_URL ? `${window.API_BASE_URL}/api/save-feedback` : 'http://localhost:8080/api/save-feedback'), true);
            xhr.setRequestHeader('Content-Type', 'application/json');
            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    console.log('XHR Status:', xhr.status);
                    console.log('XHR Response:', xhr.responseText);
                }
            };
            xhr.send(JSON.stringify({
                name: name,
                address: address,
                rating: rating,
                review_text: reviewText
            }));
        });

        // For development testing:
        console.log('Request sent to server - Name:', name, 'Address:', address, 'Rating:', rating, 'Review:', reviewText);
    }
});

// Social Share Drawer
document.addEventListener('DOMContentLoaded', function() {
    const shareDrawer = document.querySelector('.share-drawer');
    const shareToggle = document.querySelector('.share-toggle');
    const shareIcons = document.querySelectorAll('.share-icon');

    // Toggle drawer with slide effect
    shareToggle.addEventListener('click', function(e) {
        e.stopPropagation();
        shareDrawer.classList.toggle('active');
    });

    // Close drawer when clicking outside
    document.addEventListener('click', function(e) {
        if (!shareDrawer.contains(e.target)) {
            shareDrawer.classList.remove('active');
        }
    });

    // Share functionality
    shareIcons.forEach(icon => {
        icon.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            const platform = this.classList[1];
            const url = encodeURIComponent(window.location.href);
            const text = encodeURIComponent('Check out this awesome transport service!');
            
            let shareUrl;
            switch(platform) {
                case 'whatsapp':
                    shareUrl = `https://wa.me/?text=${text} ${url}`;
                    break;
                case 'facebook':
                    shareUrl = `https://www.facebook.com/sharer/sharer.php?u=${url}`;
                    break;
                
                case 'email':
                    shareUrl = `mailto:?subject=Check this out&body=${text} ${url}`;
                    break;
            }
            
            window.open(shareUrl, '_blank');
        });
    });
});



// Function to hide/show slide bars on scroll
let lastScrollTop = 0;
window.addEventListener('scroll', function() {
    const reviewSidebar = document.querySelector('.review-sidebar');
    const shareDrawer = document.querySelector('.share-drawer');
    const st = window.pageYOffset || document.documentElement.scrollTop;

    if (st > lastScrollTop) {
        // Scroll down
        reviewSidebar.classList.add('hidden'); // Add hidden class to hide
        shareDrawer.classList.add('hidden'); // Add hidden class to hide
    } else {
        // Scroll up
        reviewSidebar.classList.remove('hidden'); // Remove hidden class to show
        shareDrawer.classList.remove('hidden'); // Remove hidden class to show
    }
    lastScrollTop = st <= 0 ? 0 : st; // For Mobile or negative scrolling
});

// Show More Functionality for About Us
document.addEventListener('DOMContentLoaded', function() {
    const showMoreBtn = document.querySelector('.show-more-btn');
    const moreText = document.querySelector('.about-text-more');

    if (showMoreBtn && moreText) {
        showMoreBtn.addEventListener('click', function() {
            if (moreText.style.display === 'none' || !moreText.style.display) {
                moreText.style.display = 'block';
                showMoreBtn.textContent = 'Show Less';
            } else {
                moreText.style.display = 'none';
                showMoreBtn.textContent = 'Show More';
            }
        });
    }
});


// Smooth Scrolling for Quick Links
document.addEventListener('DOMContentLoaded', function() {
    // Handle scroll to top (Home link)
    const scrollToTopLink = document.querySelector('.scroll-to-top');
    if (scrollToTopLink) {
        scrollToTopLink.addEventListener('click', function(e) {
            e.preventDefault();
            window.scrollTo({
                top: 0,
                behavior: 'smooth'
            });
        });
    }

    // Handle scroll to sections
    const scrollLinks = document.querySelectorAll('.scroll-link');
    scrollLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const targetId = this.getAttribute('href');
            const targetSection = document.querySelector(targetId);
            
            if (targetSection) {
                const offset = 80; // Adjust this value based on your navbar height
                const targetPosition = targetSection.offsetTop - offset;
                
                window.scrollTo({
                    top: targetPosition,
                    behavior: 'smooth'
                });
            }
        });
    });
});

// Vehicle Type Selection
const vehicleSelectBtn = document.getElementById('vehicleSelectBtn');
const vehicleDropdown = document.querySelector('.vehicle-dropdown');
const vehicleOptions = document.querySelectorAll('input[name="vehicleType"]');
const locationSelection = document.querySelector('.location-selection');

// Toggle dropdown
vehicleSelectBtn.addEventListener('click', () => {
    vehicleDropdown.classList.toggle('active');
});

// Close dropdown when clicking outside
document.addEventListener('click', (e) => {
    if (!vehicleSelectBtn.contains(e.target) && !vehicleDropdown.contains(e.target)) {
        vehicleDropdown.classList.remove('active');
    }
});

// Handle vehicle selection
vehicleOptions.forEach(option => {
    option.addEventListener('change', (e) => {
        const selectedVehicle = e.target.value;
        vehicleSelectBtn.querySelector('span').textContent = selectedVehicle;
        vehicleDropdown.classList.remove('active');

        // Show the location selection section
        locationSelection.style.display = 'block';
    });
});

// Function to handle profile link based on user contact
// Check if user is logged in
// and update the profile link accordingly
document.addEventListener("DOMContentLoaded", function() {
    let userContact = localStorage.getItem("userContact");
    
    let profileLink = document.getElementById("profileLink"); 

    if (userContact) {
        profileLink.style.display = "inline-block"; // Show profile link
    }else{
        profileLink.style.display = "none";// hide rakho agar login nahi h 
    }
});


