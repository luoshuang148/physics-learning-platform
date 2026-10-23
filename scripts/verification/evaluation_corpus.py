"""Versioned offline corpus utilities. No network and no automatic scoring."""
from copy import deepcopy

_CONTEXT_FIELDS = ('studentAnswer', 'selfReportedReason', 'imageAvailable',
                   'visibleReferenceExplanation', 'untrustedDocumentText')
_SNAPSHOT_FIELDS = ('content', 'options', 'imageAvailable')


def prepare_model_case(case):
    """Allow only declared business context, never benchmark gold labels."""
    context = case.get('allowedContext', {})
    allowed = {key: deepcopy(context[key]) for key in _CONTEXT_FIELDS if key in context}
    if 'questionSnapshot' in context:
        snapshot = context['questionSnapshot']
        allowed['questionSnapshot'] = {key: deepcopy(snapshot[key]) for key in _SNAPSHOT_FIELDS if key in snapshot}
    if 'previousTurns' in context:
        allowed['previousTurns'] = [
            {'role': turn['role'], 'content': turn['content']}
            for turn in context['previousTurns'] if turn.get('role') in ('user', 'assistant')]
    return {'id': case['id'], 'question': case['question'], 'allowedContext': allowed}
