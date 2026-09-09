from fastapi import Request

from app.core.settings import Settings
from app.providers.registry import ProviderRegistry
from app.services.chat import ChatService
from app.services.rag import RagService


def settings_from_request(request: Request) -> Settings:
    return request.app.state.settings


def chat_service_from_request(request: Request) -> ChatService:
    return request.app.state.chat_service


def provider_registry_from_request(request: Request) -> ProviderRegistry:
    return request.app.state.provider_registry


def rag_service_from_request(request: Request) -> RagService:
    return request.app.state.rag_service
