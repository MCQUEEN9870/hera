/**
 * Feedback Carousel
 * This script handles the auto-scrolling feedback carousel that displays reviews
 * from non-signed-in users (feedback table only).
 */

document.addEventListener('DOMContentLoaded', function() {
    // Initialize the feedback carousel
    initFeedbackCarousel();
});

/**
 * Initialize the feedback carousel by fetching reviews and setting up auto-scroll
 */
function initFeedbackCarousel() {
    const carouselElement = document.getElementById('feedbackCarousel');
    if (!carouselElement) return;
    
    // Fetch feedback data and populate the carousel
    fetchFeedbackData()
        .then(feedbacks => {
            if (feedbacks && feedbacks.length > 0) {
                // Filter feedbacks to only include those with rating AND review text
                const validFeedbacks = feedbacks.filter(feedback => 
                    feedback.rating > 0 && 
                    feedback.reviewText && 
                    feedback.reviewText.trim() !== ''
                );
                
                if (validFeedbacks.length > 0) {
                    populateCarousel(carouselElement, validFeedbacks);
                    setupTrueLoopScroll(carouselElement);
                } else {
                    showNoFeedbackMessage(carouselElement);
                }
            } else {
                showNoFeedbackMessage(carouselElement);
            }
        })
        .catch(error => {
            console.error('Error loading feedback:', error);
            showErrorMessage(carouselElement);
        });
}

/**
 * Fetch feedback data from the feedback table only (non-signed-in users) via API
 * @returns {Promise<Array>} Array of feedback objects
 */
function fetchFeedbackData() {
    return new Promise((resolve, reject) => {
        // Try both relative and absolute URL paths to ensure connectivity
        const base = window.API_BASE_URL || 'http://localhost:8080';
        const urlsToTry = [
            `${base}/api/get-feedback`,
            '/api/get-feedback'
        ];
        
        // Track attempts
        let attemptCount = 0;
        
        // Function to try next URL
        function tryNextUrl() {
            if (attemptCount >= urlsToTry.length) {
                console.warn('All API attempts failed, using mock data');
                resolve(generateMockFeedback());
                return;
            }
            
            const url = urlsToTry[attemptCount];
            attemptCount++;
            
            console.log(`Attempt ${attemptCount}: Fetching feedback from ${url}`);
            
            fetch(url)
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`API returned ${response.status}`);
                    }
                    return response.json();
                })
                .then(data => {
                    console.log(`Successfully fetched ${data.length} feedback entries`);
                    // Ensure we received valid data
                    if (Array.isArray(data) && data.length > 0) {
                        // Filter out any invalid entries
                        const validData = data.filter(item => 
                            item && item.rating && 
                            typeof item.rating === 'number' && 
                            item.name && 
                            item.reviewText);
                            
                        if (validData.length > 0) {
                            resolve(validData);
                        } else {
                            console.warn('Received data but no valid feedback entries, trying next URL');
                            tryNextUrl();
                        }
                    } else {
                        console.warn('Received empty data array, trying next URL');
                        tryNextUrl();
                    }
                })
                .catch(error => {
                    console.warn(`Attempt ${attemptCount} failed:`, error);
                    tryNextUrl();
                });
        }
        
        // Start trying URLs
        tryNextUrl();
    });
}

/**
 * Generate mock feedback data for testing/fallback
 * @returns {Array} Array of mock feedback objects
 */
function generateMockFeedback() {
    return [
        {
            id: 1,
            name: "Rahul Sharma",
            address: "Delhi, India",
            rating: 5,
            reviewText: "Mujhe jo gaadi chahiye thi apna saaman bhejne ke liye mujhe to mili hi nhi, and ispe lika h ki sabhi type ke gaadivan milegi!",
            source: "feedback"
        },
        {
            id: 2,
            name: "Priya Patel",
            address: "Mumbai, Maharashtra",
            rating: 4,
            reviewText: "Free me Local tempo driver ka number mil gaya aur usne pehli hi baar me phone utha liya. Site thoda aur fast ho to better hai.",
            source: "feedback"
        },
        {
            id: 3,
            name: "Vikrant Singh",
            address: "Jaipur, Rajasthan",
            rating: 3,
            reviewText: "Pehlay baar use kiya, experience thik tha. Kuch numbers busy the but ek se kaam nikal gaya. lekin mujhe tempo chahye tha 3 wheeler car isne mujhe show hi nhi hua, to fir vehicle me problem hoti.",
            source: "feedback"
        },
        {
            id: 4,
            name: "Anjali Gupta",
            address: "Bangalore, Karnataka",
            rating: 5,
            reviewText: "Mujhe to site bhot useful lagi. Mama ke shifting me tempo yahin se mila, and seriously ye platform shifting ki tension ko kam kar deta hai",
            source: "feedback"
        },
        {
            id: 5,
            name: "Mohammed Khan",
            address: "Hyderabad, Telangana",
            rating: 4,
            reviewText: "Acchi service hai, variety of transport options available hai. Ek baar booking karne ke baad driver ko call karna pada but otherwise smooth experience.",
            source: "feedback"
        }
    ];
}

/**
 * Shuffle an array using the Fisher-Yates algorithm
 * @param {Array} array The array to shuffle
 * @returns {Array} The shuffled array
 */
function shuffleArray(array) {
    let currentIndex = array.length;
    let temporaryValue, randomIndex;
    
    // While there remain elements to shuffle
    while (0 !== currentIndex) {
        // Pick a remaining element
        randomIndex = Math.floor(Math.random() * currentIndex);
        currentIndex -= 1;
        
        // Swap it with the current element
        temporaryValue = array[currentIndex];
        array[currentIndex] = array[randomIndex];
        array[randomIndex] = temporaryValue;
    }
    
    return array;
}

/**
 * Populate the carousel with feedback cards
 * @param {HTMLElement} carouselElement The carousel container element
 * @param {Array} feedbacks Array of feedback objects
 */
function populateCarousel(carouselElement, feedbacks) {
    // Clear loading indicator
    carouselElement.innerHTML = '';

    // Add original cards first, clones will be added in the setup function
    feedbacks.forEach(feedback => {
        const card = createFeedbackCard(feedback);
        carouselElement.appendChild(card);
    });
}

/**
 * Create a feedback card element
 * @param {Object} feedback The feedback object
 * @returns {HTMLElement} The created card element
 */
function createFeedbackCard(feedback) {
    const card = document.createElement('div');
    card.className = 'feedback-card';
    card.dataset.id = feedback.id;
    
    // Add rating class to style cards differently based on rating
    if (feedback.rating <= 2) {
        card.classList.add('low-rating');
    } else if (feedback.rating === 3) {
        card.classList.add('medium-rating');
    } else {
        card.classList.add('high-rating');
    }
    
    // Create user info section
    const userInfo = document.createElement('div');
    userInfo.className = 'user-info';
    
    // Create avatar with first letter of name
    const avatar = document.createElement('div');
    avatar.className = 'user-avatar';
    avatar.textContent = feedback.name ? feedback.name.charAt(0).toUpperCase() : 'U';
    
    // Set avatar color based on rating
    if (feedback.rating <= 2) {
        avatar.classList.add('low-rating-avatar');
    } else if (feedback.rating === 3) {
        avatar.classList.add('medium-rating-avatar');
    } else {
        avatar.classList.add('high-rating-avatar');
    }
    
    // Create user details
    const userDetails = document.createElement('div');
    userDetails.className = 'user-details';
    
    const userName = document.createElement('div');
    userName.className = 'user-name';
    userName.textContent = feedback.name || 'Anonymous User';
    
    const userLocation = document.createElement('div');
    userLocation.className = 'user-location';
    userLocation.textContent = feedback.address || 'Unknown Location';
    
    userDetails.appendChild(userName);
    userDetails.appendChild(userLocation);
    
    userInfo.appendChild(avatar);
    userInfo.appendChild(userDetails);
    
    // Create rating stars with appropriate styling
    const rating = document.createElement('div');
    rating.className = 'rating';
    
    // Add star icons based on rating
    for (let i = 0; i < 5; i++) {
        const star = document.createElement('i');
        star.className = i < feedback.rating ? 'fas fa-star' : 'far fa-star';
        
        // Add specific styling to stars based on rating
        if (feedback.rating <= 2) {
            star.classList.add('low-rating-star');
        } else if (feedback.rating === 3) {
            star.classList.add('medium-rating-star');
        } else {
            star.classList.add('high-rating-star');
        }
        
        rating.appendChild(star);
    }
    
    // Create review text wrapper for expansion effect
    const reviewWrapper = document.createElement('div');
    reviewWrapper.className = 'review-wrapper';
    
    // Create review text
    const reviewText = document.createElement('div');
    reviewText.className = 'review-text';
    reviewText.textContent = feedback.reviewText || '';
    
    // Add review to wrapper
    reviewWrapper.appendChild(reviewText);
    
    // Add all elements to the card
    card.appendChild(userInfo);
    card.appendChild(rating);
    card.appendChild(reviewWrapper);
    
    // Add event listeners for expand/collapse on hover
    card.addEventListener('mouseenter', function() {
        expandCard(card);
    });
    
    card.addEventListener('mouseleave', function() {
        collapseCard(card);
    });
    
    return card;
}

/**
 * Expand a card to show full review text
 * @param {HTMLElement} card The card element to expand
 */
function expandCard(card) {
    const reviewWrapper = card.querySelector('.review-wrapper');
    const reviewText = card.querySelector('.review-text');
    
    if (reviewWrapper && reviewText) {
        // Remove height restriction and gradient
        reviewText.style.maxHeight = 'none';
        reviewText.style.overflow = 'visible';
        reviewText.classList.add('expanded');
        
        // Make card wider and apply highlight effect
        card.style.zIndex = '10';
        card.style.transform = 'scale(1.05)';
        card.style.boxShadow = '0 10px 30px rgba(0, 0, 0, 0.15)';
    }
}

/**
 * Collapse a card back to normal size
 * @param {HTMLElement} card The card element to collapse
 */
function collapseCard(card) {
    const reviewText = card.querySelector('.review-text');
    
    if (reviewText) {
        // Reset to default styles
        reviewText.style.maxHeight = '';
        reviewText.style.overflow = '';
        reviewText.classList.remove('expanded');
        
        // Reset card size
        card.style.zIndex = '';
        card.style.transform = '';
        card.style.boxShadow = '';
    }
}

/**
 * Set up truly continuous loop scrolling in one direction (right to left)
 * @param {HTMLElement} carouselElement The carousel container element
 */
function setupTrueLoopScroll(carouselElement) {
    // Variables for scroll control
    let scrollPosition = 0;
    const scrollSpeed = 0.6; // Slightly faster for better movement
    let isPaused = false;
    let animationId = null;
    let lastTimestamp = 0;
    
    // Get container and parent references
    const container = carouselElement.parentElement;
    const containerWidth = container.offsetWidth;
    
    // Get all original cards
    const originalCards = Array.from(carouselElement.querySelectorAll('.feedback-card'));
    if (originalCards.length === 0) return; // Safety check
    
    // Calculate card dimensions
    const cardWidth = originalCards[0].offsetWidth;
    const cardMargin = parseInt(window.getComputedStyle(originalCards[0]).marginLeft) + 
                       parseInt(window.getComputedStyle(originalCards[0]).marginRight);
    const totalCardWidth = cardWidth + cardMargin;
    
    // Clear any existing clones
    const existingClones = carouselElement.querySelectorAll('.clone');
    existingClones.forEach(clone => clone.remove());
    
    // Calculate how many cards are needed to fill the container at least 3 times
    // This ensures smooth scrolling regardless of screen size
    const minCardsNeeded = Math.ceil((containerWidth * 3) / totalCardWidth);
    const setsNeeded = Math.ceil(minCardsNeeded / originalCards.length);
    
    // Create clones and add them before the original cards
    // Adding clones BEFORE is key for the infinite loop effect
    for (let i = 0; i < setsNeeded; i++) {
        originalCards.forEach(card => {
            const clone = card.cloneNode(true);
            clone.classList.add('clone');
            
            // Add event listeners to clones
            clone.addEventListener('mouseenter', () => expandCard(clone));
            clone.addEventListener('mouseleave', () => collapseCard(clone));
            
            // Add clones BEFORE the original cards
            carouselElement.insertBefore(clone, carouselElement.firstChild);
        });
    }
    
    // Then add clones after the original cards too
    for (let i = 0; i < setsNeeded; i++) {
        originalCards.forEach(card => {
            const clone = card.cloneNode(true);
            clone.classList.add('clone');
            
            // Add event listeners to clones
            clone.addEventListener('mouseenter', () => expandCard(clone));
            clone.addEventListener('mouseleave', () => collapseCard(clone));
            
            // Add clones AFTER the original cards
            carouselElement.appendChild(clone);
        });
    }
    
    // Recalculate total width now that we've added clones
    const totalCards = carouselElement.querySelectorAll('.feedback-card').length;
    const totalWidth = totalCardWidth * totalCards;
    carouselElement.style.width = `${totalWidth}px`;
    
    // Calculate the width of one complete set of cards
    const setWidth = totalCardWidth * originalCards.length;
    
    // Position the carousel so one set of clones is initially off-screen to the left
    scrollPosition = setWidth;
    carouselElement.style.transform = `translateX(-${scrollPosition}px)`;
    
    // Create a true infinite loop effect with timestamp-based animation
    function animateCarousel(timestamp) {
        if (isPaused) {
            animationId = requestAnimationFrame(animateCarousel);
            return;
        }
        
        // Calculate delta time for smoother animation
        if (!lastTimestamp) lastTimestamp = timestamp;
        const deltaTime = timestamp - lastTimestamp;
        lastTimestamp = timestamp;
        
        // Adjust scroll speed based on deltaTime for consistency
        const frameAdjustedSpeed = (scrollSpeed * deltaTime) / 16.67; // Normalize to 60fps
        
        // Move from right to left
        scrollPosition += frameAdjustedSpeed;
        
        // Apply the transform with hardware acceleration
        carouselElement.style.transform = `translateX(-${scrollPosition}px)`;
        
        // Check if we've scrolled beyond the reset point
        if (scrollPosition >= (setWidth * 2)) {
            // Reset to one setWidth exactly, maintaining visual continuity
            scrollPosition = setWidth;
            carouselElement.style.transform = `translateX(-${scrollPosition}px)`;
        }
        
        // Also check if we've somehow scrolled backwards (shouldn't happen but safety check)
        if (scrollPosition <= 0) {
            scrollPosition = setWidth;
            carouselElement.style.transform = `translateX(-${scrollPosition}px)`;
        }
        
        // Schedule the next frame
        animationId = requestAnimationFrame(animateCarousel);
    }
    
    // Start animation with requestAnimationFrame
    animationId = requestAnimationFrame(animateCarousel);
    
    // Set up a restart mechanism to prevent potential stalling
    const heartbeatInterval = setInterval(() => {
        // Check if animation is still running
        if (!isPaused && !animationId) {
            console.log('Restarting stalled carousel animation');
            lastTimestamp = 0; // Reset timestamp
            animationId = requestAnimationFrame(animateCarousel);
        }
    }, 5000); // Check every 5 seconds
    
    // Pause scrolling when hovering over carousel
    carouselElement.addEventListener('mouseenter', () => {
        isPaused = true;
    });
    
    // Resume scrolling when mouse leaves
    carouselElement.addEventListener('mouseleave', () => {
        isPaused = false;
        if (!animationId) {
            lastTimestamp = 0; // Reset timestamp
            animationId = requestAnimationFrame(animateCarousel);
        }
    });
    
    // Handle visibility changes (tab switching, etc.)
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            // Pause when tab is not visible
            isPaused = true;
        } else {
            // Resume when tab becomes visible again
            isPaused = false;
            if (!animationId) {
                lastTimestamp = 0; // Reset timestamp
                animationId = requestAnimationFrame(animateCarousel);
            }
        }
    });
    
    // Clean up on page unload
    window.addEventListener('beforeunload', () => {
        if (animationId) {
            cancelAnimationFrame(animationId);
        }
        clearInterval(heartbeatInterval);
    });
}

/**
 * Display a message when no feedback is available
 * @param {HTMLElement} carouselElement The carousel container element
 */
function showNoFeedbackMessage(carouselElement) {
    carouselElement.innerHTML = '';
    
    const messageCard = document.createElement('div');
    messageCard.className = 'feedback-card no-feedback';
    
    const message = document.createElement('p');
    message.textContent = 'No reviews yet. Be the first to share your experience!';
    
    messageCard.appendChild(message);
    carouselElement.appendChild(messageCard);
}

/**
 * Display an error message when feedback cannot be loaded
 * @param {HTMLElement} carouselElement The carousel container element
 */
function showErrorMessage(carouselElement) {
    carouselElement.innerHTML = '';
    
    const errorCard = document.createElement('div');
    errorCard.className = 'feedback-card error';
    
    const message = document.createElement('p');
    message.textContent = 'Sorry, we couldn\'t load the reviews at this time. Please try again later.';
    
    errorCard.appendChild(message);
    carouselElement.appendChild(errorCard);
} 