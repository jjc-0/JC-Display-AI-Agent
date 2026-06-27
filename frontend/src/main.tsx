import React from "react"
import ReactDOM from "react-dom/client"
import App from "./App"
import "./index.css"

const savedTheme = localStorage.getItem("jc-display-theme") === "dark" ? "dark" : "light"
document.documentElement.dataset.theme = savedTheme
document.documentElement.style.colorScheme = savedTheme

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
