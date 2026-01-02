#ifndef _STYLE_H_
#define _STYLE_H_

const char custom_style[] PROGMEM = R"=====(
<style>
  /* 1. DARK THEME BASE */
  body {
    background-color: #1a1a2e !important;
    color: #e0e0e0 !important;
    font-family: 'Verdana', sans-serif;
    margin: 0; 
    padding: 20px;
    text-align: center;
  }
  
  /* 2. CARD CONTAINER (Hide default wrapper style if possible and make our own) */
  div, form {
    /* Try to force elements to center */
    margin-left: auto; 
    margin-right: auto;
  }

  /* 3. HEADINGS & TEXT */
  h1, h2, h3, h4 {
    color: #16213e;
    text-shadow: none;
  }

  /* 4. BUTTONS - NEON STYLE */
  button {
    background: linear-gradient(135deg, #0f3460 0%, #16213e 100%) !important;
    border: 1px solid #e94560 !important;
    color: #fff !important;
    padding: 15px 20px !important;
    border-radius: 30px !important;
    font-size: 1.1rem !important;
    margin-bottom: 15px !important;
    cursor: pointer;
    box-shadow: 0 4px 15px rgba(233, 69, 96, 0.4);
    transition: transform 0.2s;
    width: 100%;
    max-width: 300px;
    display: block;
    margin-left: auto;
    margin-right: auto;
  }
  button:active {
    transform: scale(0.95);
  }
  button:hover {
     background: #e94560 !important;
  }

  /* 5. INPUT FIELDS */
  input[type="text"], input[type="password"], input[type="number"] {
    background-color: #16213e !important;
    color: #fff !important;
    border: 2px solid #0f3460 !important;
    border-radius: 8px !important;
    padding: 12px !important;
    width: 90% !important;
    max-width: 300px;
    margin-bottom: 20px;
  }
  input::placeholder {
    color: #888;
  }
  
  /* 6. LINKS & OTHERS */
  a { text-decoration: none; color: #e94560 !important; }
  
  /* 7. HIDING DEFAULT JUNK */
  /* Hide the "WiFi Manager" text if it's in an H1 or specific div */
  /* Warning: This might hide our injected header if not careful */
  
  .c { color: #555 !important; font-size: 0.8rem; }

</style>

<script>
  // JS Injection to Force Header
  window.onload = function() {
    // 1. Create Header
    var header = document.createElement("div");
    header.style.padding = "20px";
    header.style.marginBottom = "20px";
    header.innerHTML = "<h1 style='color:#e94560; margin:0; font-size:2rem;'>ANTIGRAVITY</h1><p style='color:#fff;'>IoT Configuration Portal</p>";
    
    // 2. Insert at very top of body
    document.body.insertBefore(header, document.body.firstChild);
    
    // 3. Try to change the "Connect to" text colour
    var elements = document.getElementsByTagName("div");
    for(var i=0; i<elements.length; i++) {
       if(elements[i].style.textAlign == "left") {
          // centering the main container
          elements[i].style.textAlign = "center";
          elements[i].style.backgroundColor = "#eee"; // Light card for contrast
          elements[i].style.color = "#333";
          elements[i].style.borderRadius = "10px";
          elements[i].style.padding = "20px";
          elements[i].style.display = "inline-block";
       }
    }
  };
</script>
)=====";

#endif
